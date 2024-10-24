package springAop.aop;

import springAop.annotation.Around;
import springAop.exception.AopConfigException;
import springIoc.context.*;

import java.lang.reflect.InvocationHandler;
import java.util.HashMap;
import java.util.Map;

public class AroundProxyBeanPostProcessor implements BeanPostProcessor {

    Map<String,Object> originBeans = new HashMap<String,Object>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        Around anno = bean.getClass().getAnnotation(Around.class);
        if(anno!=null){
            String handlerName;
            handlerName = anno.value();
            Object proxy = createProxy(bean.getClass(), bean, handlerName);
            originBeans.put(beanName, bean);
            return proxy;
        }

        return bean;
    }

    private Object createProxy(Class<?> beanClass, Object bean, String handlerName) {
        ConfigurableApplicationContext ctx = (ConfigurableApplicationContext) ApplicationContextUtils.getApplicationContext();
        BeanDefinition def = ctx.findBeanDefinition(handlerName);
        if (def == null) {
            throw new AopConfigException();
        }
        Object handlerBean = def.getInstance();
        if (handlerBean == null) {
            handlerBean = ctx.createBeanAsEarlySingleton(def);
        }
        if (handlerBean instanceof InvocationHandler handler) {
            return ProxyResolver.getInstance().createProxy(bean, handler);
        } else {
            throw new AopConfigException();
        }
    }

    @Override
    public Object postProcessOnSetProperty(Object bean, String beanName) {
        Object origin = this.originBeans.get(beanName);
        return origin != null ? origin : bean;
    }
}
