package springIoc.context;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import springIoc.exception.*;
import springIoc.io.PropertyResolver;
import springIoc.annotation.*;
import springIoc.io.ResourceResolver;
import springIoc.utils.ClassUtils;


import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 1.首先传入启动类
 * 2.根据启动类中的scan找到包名 如果没有 则根据启动类的位置来确定包位置
 * 3.根据包位置扫描其中的类 先将com.example.hello.class 转换为 com.example.hello 然后扫描Import导入的类
 * 4.对拿到的类进行处理 开始创建对象
 * 5.循环处理4拿到的类每拿到一个 先判断是否是类然后 判断component注解 然后再判断Configuration注解
 *
 */
public class AnnotationConfigApplicationContext implements ConfigurableApplicationContext{

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final PropertyResolver propertyResolver;
    Map<String, BeanDefinition> beans;
    private List<BeanPostProcessor> beanPostProcessors;
    Set<String> creatingBeanNames;// 用于解决循环依赖的二级缓存

    public AnnotationConfigApplicationContext (Class<?> configClass, PropertyResolver propertyResolver){
        ApplicationContextUtils.setApplicationContext(this);
        this.propertyResolver = propertyResolver;
        final Set<String> beanClassNames = scanForClassNames(configClass);
        // 创建Bean的定义:
        this.beans = createBeanDefinitions(beanClassNames);
        this.creatingBeanNames = new HashSet<>();
        this.beanPostProcessors = new ArrayList<>();
        //先将工厂方法实例化才能实例化其它的bean
        this.beans.values().stream()
                .filter(this::isConfigurationDefinition).sorted().map(def->{
                    createBeanAsEarlySingleton(def);
                    return def.getName();
        }).collect(Collectors.toList());

        List<BeanPostProcessor> processors = this.beans.values().stream()
                .filter(this::isBeanPostProcessorDefinition)
                .sorted()
                .map(def->{
                    return  (BeanPostProcessor) createBeanAsEarlySingleton(def);
                }).collect(Collectors.toList());
        this.beanPostProcessors.addAll(processors);

        //创建剩余的普通Bean
        createNormalBeans();


        // 通过字段和set方法注入依赖:
        this.beans.values().forEach(def -> {
            injectBean(def);
        });

        // 调用init方法:
        this.beans.values().forEach(def -> {
            initBean(def);
        });

        if (logger.isDebugEnabled()) {
            this.beans.values().stream().sorted().forEach(def -> {
                logger.debug("bean initialized: {}", def);
            });
        }
    }

    private boolean isBeanPostProcessorDefinition(BeanDefinition def) {
        return BeanPostProcessor.class.isAssignableFrom(def.getBeanClass());
    }

    /**
     * 创建普通的Bean
     */
    void createNormalBeans() {
        // 获取BeanDefinition列表:
        List<BeanDefinition> defs = this.beans.values().stream()
                // filter bean definitions by not instantiation:
                .filter(def -> def.getInstance() == null).sorted().collect(Collectors.toList());

        defs.forEach(def -> {
            // 如果Bean未被创建(可能在其他Bean的构造方法注入前被创建):
            if (def.getInstance() == null) {
                // 创建Bean:
                createBeanAsEarlySingleton(def);
            }
        });
    }

    // 创建一个Bean，但不进行字段和方法级别的注入。如果创建的Bean不是Configuration，则在构造方法/工厂方法中注入的依赖Bean会自动创建
    public Object createBeanAsEarlySingleton(BeanDefinition def){
        if(!this.creatingBeanNames.add(def.getName())){
            //抛出重复创建bean导致的循环依赖
            throw  new UnsatisfiedDependencyException();
        }
        // 拿到建造方法
        Executable creatFn = def.getFactoryName() == null ? def.getConstructor() : def.getFactoryMethod();

        //拿到参数类型
        final Parameter[] parameters = creatFn.getParameters();
        //用于创建对象的参数
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            //获取参数的@Value和@Autowired
            Value value = parameters[i].getAnnotation(Value.class);
            Autowired autowired = parameters[i].getAnnotation(Autowired.class);

            // @Configuration类型的Bean是工厂，不允许使用@Autowired创建:
            final boolean isConfiguration = isConfigurationDefinition(def);
            if (isConfiguration && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify @Autowired when create @Configuration bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            // 参数需要@Value或@Autowired两者之一:
            if (value != null && autowired != null) {
                throw new BeanCreationException(
                        String.format("Cannot specify both @Autowired and @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            if (value == null && autowired == null) {
                throw new BeanCreationException(
                        String.format("Must specify @Autowired or @Value when create bean '%s': %s.", def.getName(), def.getBeanClass().getName()));
            }
            //获取参数类型
            final Class<?> type = parameters[i].getType();
            if (value != null) {
                // 参数是@Value:
                args[i] = this.propertyResolver.getRequiredProperty(value.value(), type);
            }else{
                //参数是@Autowired
                String name = autowired.name();//查看bean否写了name
                boolean required = autowired.value();
                //找到相应的beanDefinition
                BeanDefinition dependsOnDef = name.isEmpty() ? findBeanDefinition(type) : findBeanDefinition(name, type);
                // 检测required==true?
                if (required && dependsOnDef == null) {
                    throw new BeanCreationException(String.format("Missing autowired bean with type '%s' when create bean '%s': %s.", type.getName(),
                            def.getName(), def.getBeanClass().getName()));
                }
                //获取依赖
                if(dependsOnDef!=null){
                    Object autowiredBeanInstance = dependsOnDef.getInstance();
                    if (autowiredBeanInstance == null && !isConfiguration) {
                        // 当前依赖Bean尚未初始化，递归调用初始化该依赖Bean:
                        autowiredBeanInstance = createBeanAsEarlySingleton(dependsOnDef);
                    }
                    args[i] = autowiredBeanInstance;
                }else{
                    args[i]=null;
                }
            }
        }
        // 创建Bean实例:
        Object instance = null;
        if (def.getFactoryName() == null) {
            // 用构造方法创建:
            try {
                instance = def.getConstructor().newInstance(args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        } else {
            // 用@Bean方法创建:
            //先拿到工厂方法的实例
            Object configInstance = getBean(def.getFactoryName());
            try {
                instance = def.getFactoryMethod().invoke(configInstance, args);
            } catch (Exception e) {
                throw new BeanCreationException(String.format("Exception when create bean '%s': %s", def.getName(), def.getBeanClass().getName()), e);
            }
        }
        def.setInstance(instance);
        //开始检查代理
        for (BeanPostProcessor processor : beanPostProcessors) {
            Object processed = processor.postProcessBeforeInitialization(def.getInstance(), def.getName());
            // 如果一个BeanPostProcessor替换了原始Bean，则更新Bean的引用:
            // 具体的判断是否是代理类的方式放在每个代理类中
            if (def.getInstance() != processed) {
                def.setInstance(processed);
            }
        }
        return def.getInstance();
    }


    void checkFieldOrMethod(Member m) {
        int mod = m.getModifiers();
        if (Modifier.isStatic(mod)) {
            throw new BeanDefinitionException("Cannot inject static field: " + m);
        }
        if (Modifier.isFinal(mod)) {
            if (m instanceof Field) {
                Field field = (Field) m;
                throw new BeanDefinitionException("Cannot inject final field: " + field);
            }

            if (m instanceof Method) {
                Method method = (Method) m;
                logger.warn("Inject final method should be careful because it is not called on target bean when bean is proxied and may cause NullPointerException.");
            }
        }
    }

    /**
     * 通过Name查找Bean，不存在时抛出NoSuchBeanDefinitionException
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(String name) {
        BeanDefinition def = this.beans.get(name);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s'.", name));
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 通过Name和Type查找Bean，不存在抛出NoSuchBeanDefinitionException，存在但与Type不匹配抛出BeanNotOfRequiredTypeException
     */
    public <T> T getBean(String name, Class<T> requiredType) {
        T t = findBean(name, requiredType);
        if (t == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with name '%s' and type '%s'.", name, requiredType));
        }
        return t;
    }

    /**
     * 通过Type查找Beans
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> getBeans(Class<T> requiredType) {
        List<BeanDefinition> defs = findBeanDefinitions(requiredType);
        if (defs.isEmpty()) {
            return List.of();
        }
        List<T> list = new ArrayList<>(defs.size());
        for (var def : defs) {
            list.add((T) def.getRequiredInstance());
        }
        return list;
    }

    @Override
    public void close() {
        logger.info("Closing {}...", this.getClass().getName());
        this.beans.values().forEach(def -> {
            final Object beanInstance = getProxiedInstance(def);
            callMethod(beanInstance, def.getDestroyMethod(), def.getDestroyMethodName());
        });
        this.beans.clear();
        logger.info("{} closed.", this.getClass().getName());
        ApplicationContextUtils.setApplicationContext(null);
    }

    /**
     * 通过Type查找Bean，不存在抛出NoSuchBeanDefinitionException，存在多个但缺少唯一@Primary标注抛出NoUniqueBeanDefinitionException
     */
    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            throw new NoSuchBeanDefinitionException(String.format("No bean defined with type '%s'.", requiredType));
        }
        return (T) def.getRequiredInstance();
    }

    /**
     * 检测是否存在指定Name的Bean
     */
    public boolean containsBean(String name) {
        return this.beans.containsKey(name);
    }



    private Map<String, BeanDefinition> createBeanDefinitions(Set<String> beanClassNames) {
        //对标注了ComponentScan中的类进行处理
        Map<String, BeanDefinition> defs = new HashMap<>();
        for (String className  : beanClassNames) {
            Class<?> clazz =null;
            try {
                clazz=Class.forName(className);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (clazz.isAnnotation() || clazz.isEnum() || clazz.isInterface() || clazz.isRecord()) {
                continue;
            }
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if(component!=null){
                logger.atDebug().log("found component: {}", clazz.getName());
                int mod = clazz.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be abstract.");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be private.");
                }
                //拿到bean的name
                String beanName = ClassUtils.getBeanName(clazz);
                BeanDefinition def = new BeanDefinition(
                        beanName, clazz, getSuitableConstructor(clazz),
                        getOrder(clazz), clazz.isAnnotationPresent(Primary.class),
                        // init/destroy方法名称:
                        null, null,
                        // 查找@PostConstruct方法:
                        ClassUtils.findAnnotationMethod(clazz, PostConstruct.class),
                        // 查找@PreDestroy方法:
                        ClassUtils.findAnnotationMethod(clazz, PreDestroy.class));
                addBeanDefinitions(defs, def);
                logger.atDebug().log("define bean: {}", def);
                Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
                if(configuration!=null){
                    //对于Configuration的注解 我们视为bean工厂 继续进行处理
                    scanFactoryMethods(beanName,clazz,defs);
                }
            }
        }
        return defs;
    }

    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> defs){
        //工厂要查找的是方法
        for (Method method : clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if(bean!=null){
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract.");
                }
                if (Modifier.isFinal(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be final.");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
                }
                Class<?> beanClass = method.getReturnType();
                if (beanClass.isPrimitive()) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return primitive type.");
                }
                if (beanClass == void.class || beanClass == Void.class) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
                }

                BeanDefinition def = new BeanDefinition(
                        ClassUtils.getBeanName(method), beanClass,
                        factoryBeanName,
                        // 创建Bean的工厂方法:
                        method,
                        // @Order
                        getOrder(method),
                        // 是否存在@Primary标注?
                        method.isAnnotationPresent(Primary.class),
                        // init方法名称:
                        bean.initMethod().isEmpty() ? null : bean.initMethod(),
                        // destroy方法名称:
                        bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                        // @PostConstruct / @PreDestroy方法:
                        null, null);
                addBeanDefinitions(defs,def);
                logger.atDebug().log("define bean: {}", def);
            }
        }
    }

    void addBeanDefinitions(Map<String, BeanDefinition> defs, BeanDefinition def) {
        if (defs.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }



    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        Constructor<?>[] cons = clazz.getConstructors();
        if (cons.length == 0) {
            cons = clazz.getDeclaredConstructors();
            if (cons.length != 1) {
                throw new BeanDefinitionException("More than one constructor found in class " + clazz.getName() + ".");
            }
        }
        if (cons.length != 1) {
            throw new BeanDefinitionException("More than one public constructor found in class " + clazz.getName() + ".");
        }
        return cons[0];
    }

    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    private Set<String> scanForClassNames(Class<?> configClass) {
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        //如果没有则扫描ComponentScan所在的包
        final String[] scanPackages = scan == null || scan.value().length==0? new String[]{configClass.getPackage().getName()}: scan.value();
        logger.atInfo().log("component scan in packages: {}", Arrays.toString(scanPackages));
        Set<String> classNameSet =new HashSet<>();

        for (String scanPackage : scanPackages) {
            logger.atDebug().log("scan package: {}", scanPackage);
            ResourceResolver resourceResolver = new ResourceResolver(scanPackage);
            List<String> classList = resourceResolver.scan(res->{
                String name = res.getName();
                //将com.example.hello.class 转化为 com.example.hello
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            if (logger.isDebugEnabled()) {
                classList.forEach((className) -> {
                    logger.debug("class found by component scan: {}", className);
                });
            }
            classNameSet.addAll(classList);
        }

        Import importConfig = configClass.getAnnotation(Import.class);
        if(importConfig != null){
            for (Class<?> importConfigClass  : importConfig.value()) {
                String importClassName  = importConfigClass.getName();
                if (classNameSet.contains(importClassName)) {
                    logger.warn("ignore import: " + importClassName + " for it is already been scanned.");
                } else {
                    logger.debug("class found by import: {}", importClassName);
                    classNameSet.add(importClassName);
                }
            }
        }
        return classNameSet;
    }

    //查找符合类型的所有bean
    public List<BeanDefinition> findBeanDefinitions(Class<?> type){
        return this.beans.values().stream()
                .filter(item->type.isAssignableFrom(item.getBeanClass()))
                .sorted().collect(Collectors.toList());
    }


    //找到唯一的bean
    @Nullable
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> beanDefinitions = findBeanDefinitions(type);
        if(beanDefinitions.isEmpty()){
            return null;
        }
        if(beanDefinitions.size()==1){
            return beanDefinitions.get(0);
        }

        List<BeanDefinition> list = beanDefinitions.stream().filter(item -> item.isPrimary()).collect(Collectors.toList());

        if(list.size()==1){
            return list.get(0);
        }

        if(list.isEmpty()){//不存在主要类
            throw new NoUniqueBeanDefinitionException(String.format("Multiple bean with type '%s' found, but no @Primary specified.", type.getName()));
        }else{ //主要类不唯一
            throw new NoUniqueBeanDefinitionException(String.format("Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }


    boolean isConfigurationDefinition(BeanDefinition def) {
        return ClassUtils.findAnnotation(def.getBeanClass(), Configuration.class) != null;
    }

    /**
     * 根据Name查找BeanDefinition，如果Name不存在，返回null
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    /**
     * 根据Name和Type查找BeanDefinition，如果Name不存在，返回null，如果Name存在，但Type不匹配，抛出异常。
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        if (!requiredType.isAssignableFrom(def.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format("Autowire required type '%s' but bean '%s' has actual type '%s'.", requiredType.getName(),
                    name, def.getBeanClass().getName()));
        }
        return def;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(String name, Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(name, requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> T findBean(Class<T> requiredType) {
        BeanDefinition def = findBeanDefinition(requiredType);
        if (def == null) {
            return null;
        }
        return (T) def.getRequiredInstance();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    protected <T> List<T> findBeans(Class<T> requiredType) {
        return findBeanDefinitions(requiredType).stream().map(def -> (T) def.getRequiredInstance()).collect(Collectors.toList());
    }


    /**
     * 注入依赖但不调用init方法
     */
    void injectBean(BeanDefinition def) {
        //依赖注入要注入到原始类中
        // 获取Bean实例，或被代理的原始实例:
        Object beanInstance = getProxiedInstance(def);
        try {
            injectProperties(def, def.getBeanClass(), beanInstance);
        } catch (ReflectiveOperationException e) {
            throw new BeanCreationException(e);
        }
    }

    private Object getProxiedInstance(BeanDefinition def) {
        Object beaninstance = def.getInstance();
        List<BeanPostProcessor> reversedBeanPostProcessors = new ArrayList<>(this.beanPostProcessors);
        Collections.reverse(reversedBeanPostProcessors);
        for (BeanPostProcessor beanPostProcessor : reversedBeanPostProcessors) {
            Object restoredInstance  = beanPostProcessor.postProcessOnSetProperty(beaninstance, def.getName());
            if(restoredInstance!=beaninstance){
                beaninstance = restoredInstance;
            }
        }

        return beaninstance;

    }

    /**
     * 调用init方法
     */
    void initBean(BeanDefinition def) {
        // 调用init方法:
        callMethod(def.getInstance(), def.getInitMethod(), def.getInitMethodName());
    }

    private void callMethod(Object beanInstance, Method method, String namedMethod) {
        // 调用init/destroy方法:
        if (method != null) {
            try {
                method.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        } else if (namedMethod != null) {
            // 查找initMethod/destroyMethod="xyz"，注意是在实际类型中查找:
            Method named = ClassUtils.getNamedMethod(beanInstance.getClass(), namedMethod);
            named.setAccessible(true);
            try {
                named.invoke(beanInstance);
            } catch (ReflectiveOperationException e) {
                throw new BeanCreationException(e);
            }
        }
    }

    /**
     * 为方法字段和类字段注入属性
     */
    void injectProperties(BeanDefinition def, Class<?> clazz, Object bean) throws ReflectiveOperationException {
        // 在当前类查找Field和Method并注入:
        for (Field f : clazz.getDeclaredFields()) {
            tryInjectProperties(def, clazz, bean, f);
        }
        for (Method m : clazz.getDeclaredMethods()) {
            tryInjectProperties(def, clazz, bean, m);
        }
        // 在父类查找Field和Method并注入:
        Class<?> superClazz = clazz.getSuperclass();
        if (superClazz != null) {
            injectProperties(def, superClazz, bean);
        }
    }

    /**
     * 单个内容注入实际步骤
     */
    void tryInjectProperties(BeanDefinition def, Class<?> clazz, Object bean, AccessibleObject acc) throws ReflectiveOperationException {
        Value value = acc.getAnnotation(Value.class);
        Autowired autowired = acc.getAnnotation(Autowired.class);
        if (value == null && autowired == null) {
            return;
        }

        Field field = null;
        Method method = null;
        if (acc instanceof Field ) {
            Field f = (Field) acc;
            checkFieldOrMethod(f);
            f.setAccessible(true);
            field = f;
        }
        if (acc instanceof Method ) {
            Method m = (Method) acc;
            checkFieldOrMethod(m);
            if (m.getParameters().length != 1) {
                //限制了只有一个参数的才能注入
                throw new BeanDefinitionException(
                        String.format("Cannot inject a non-setter method %s for bean '%s': %s", m.getName(), def.getName(), def.getBeanClass().getName()));
            }
            m.setAccessible(true);
            method = m;
        }

        String accessibleName = field != null ? field.getName() : method.getName();
        Class<?> accessibleType = field != null ? field.getType() : method.getParameterTypes()[0];

        if (value != null && autowired != null) {
            throw new BeanCreationException(String.format("Cannot specify both @Autowired and @Value when inject %s.%s for bean '%s': %s",
                    clazz.getSimpleName(), accessibleName, def.getName(), def.getBeanClass().getName()));
        }

        // @Value注入:
        if (value != null) {
            Object propValue = this.propertyResolver.getRequiredProperty(value.value(), accessibleType);
            if (field != null) {
                logger.atDebug().log("Field injection: {}.{} = {}", def.getBeanClass().getName(), accessibleName, propValue);
                field.set(bean, propValue);
            }
            if (method != null) {
                logger.atDebug().log("Method injection: {}.{} ({})", def.getBeanClass().getName(), accessibleName, propValue);
                method.invoke(bean, propValue);
            }
        }

        // @Autowired注入:
        if (autowired != null) {
            String name = autowired.name();
            boolean required = autowired.value();
            Object depends = name.isEmpty() ? findBean(accessibleType) : findBean(name, accessibleType);
            if (required && depends == null) {
                throw new UnsatisfiedDependencyException(String.format("Dependency bean not found when inject %s.%s for bean '%s': %s", clazz.getSimpleName(),
                        accessibleName, def.getName(), def.getBeanClass().getName()));
            }
            if (depends != null) {
                if (field != null) {
                    logger.atDebug().log("Field injection: {}.{} = {}", def.getBeanClass().getName(), accessibleName, depends);
                    field.set(bean, depends);
                }
                if (method != null) {
                    logger.atDebug().log("Mield injection: {}.{} ({})", def.getBeanClass().getName(), accessibleName, depends);
                    method.invoke(bean, depends);
                }
            }
        }
    }

}