package springIoc.io;


import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * 配置文件的查询
 * 1.按key
 * 2.${obejct.a}的查询方式 用于@value("${obejct.a")
 * 3.拥有默认值${abc.xyz:defaultValue}  用于@Value("${app.title:Summer}")的注入
 */
public class PropertyResolver {

    Logger logger = LoggerFactory.getLogger(getClass());

    Map<String, String> properties = new HashMap<String, String>();//Java本身提供了按key-value查询的Properties
    Map<Class<?>, Function<String,Object>> converters =new HashMap<>();//存储String转化到其他类型

    public PropertyResolver(Properties props){
        //首先存入环境变量
        this.properties.putAll(System.getenv());
        //然后存入Properties中的配置项
        Set<String> strings = props.stringPropertyNames();
        for (String name : strings) {
            this.properties.put(name,props.getProperty(name));
        }

        if (logger.isDebugEnabled()) {
            List<String> keys = new ArrayList<>(this.properties.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                logger.debug("PropertyResolver: {} = {}", key, this.properties.get(key));
            }
        }

        // String类型:
        converters.put(String.class, s -> s);
        // boolean类型:
        converters.put(boolean.class, s -> Boolean.parseBoolean(s));
        converters.put(Boolean.class, s -> Boolean.valueOf(s));
        // int类型:
        converters.put(int.class, s -> Integer.parseInt(s));
        converters.put(Integer.class, s -> Integer.valueOf(s));
        // 其他基本类型...
        // Date/Time类型:
        converters.put(LocalDate.class, s -> LocalDate.parse(s));
        converters.put(LocalTime.class, s -> LocalTime.parse(s));
        converters.put(LocalDateTime.class, s -> LocalDateTime.parse(s));
        converters.put(ZonedDateTime.class, s -> ZonedDateTime.parse(s));
        converters.put(Duration.class, s -> Duration.parse(s));
        converters.put(ZoneId.class, s -> ZoneId.of(s));
    }

    <T> T convert(Class<?> clazz,String value){
        Function<String,Object> fn =this.converters.get(clazz);
        if(fn ==null){
            throw new IllegalArgumentException("Unsupported value type: " + clazz.getName());
        }
        return (T)  fn.apply(value);

    }

    @Nullable
    public String getProperty(String key) {
        //解析${abc.xyz:defaultValue}内容
        PropertyExpr keyExpr = parsePropertyExpr(key);
        if(keyExpr!=null){
            if(keyExpr.getDefaultValue()!=null){
                //按照默认值解析
                return getProperty(keyExpr.getKey(),keyExpr.getDefaultValue());
            }else{
                //此时的key
                return getRequiredProperty(keyExpr.getKey());
            }
        }

        // 普通key查询:
        String value = this.properties.get(key);
        if (value != null) {
            return parseValue(value);
        }

        return null;
    }


    /**
     * 实现的是除了String类型之外的类型
     * @param key
     * @param targetType
     * @param <T>
     * @return
     */
    public <T> T getProperty(String key,Class<T> targetType) {
        String value = getProperty(key);
        if (value == null) {
            return null;
        }
        // 转换为指定类型:
        return convert(targetType, value);
    }

    public String getProperty(String key,String defaultValue) {
        String value = getProperty(key);
     return value==null?parseValue(defaultValue):defaultValue;
    }

    String parseValue(String value) {
        PropertyExpr expr = parsePropertyExpr(value);
        if (expr == null) {
            return value;
        }
        if (expr.getDefaultValue() != null) {
            return getProperty(expr.getKey(), expr.getDefaultValue());
        } else {
            return getRequiredProperty(expr.getKey());
        }
    }

    public String getRequiredProperty(String key) {
        String value = getProperty(key);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    public <T> T getRequiredProperty(String key, Class<T> targetType) {
        T value = getProperty(key, targetType);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }


    PropertyExpr parsePropertyExpr(String key){
        if(key.startsWith("${")&&key.endsWith("}")){
            //判断是否有默认值
            int n=key.indexOf(":");
            if(n==-1){
                //没有默认值
                String k=key.substring(2,key.length()-1);
                return new PropertyExpr(k,null);
            }else{
                String k=key.substring(2,n);
                return new PropertyExpr(k, key.substring(n + 1, key.length() - 1));
            }
        }
        //格式不对返回null
        return null;
    }

    /**
     * 用于解析表达式
     */
    class PropertyExpr {
        private final String key;
        private final String defaultValue;

        // 构造函数
        public PropertyExpr(String key, String defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        // 获取key的方法
        public String getKey() {
            return key;
        }

        // 获取defaultValue的方法
        public String getDefaultValue() {
            return defaultValue;
        }

        // 重写toString方法
        @Override
        public String toString() {
            return "PropertyExpr{" +
                    "key='" + key + '\'' +
                    ", defaultValue='" + defaultValue + '\'' +
                    '}';
        }

        // 重写equals方法
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PropertyExpr)) return false;

            PropertyExpr that = (PropertyExpr) o;

            if (!key.equals(that.key)) return false;
            return defaultValue.equals(that.defaultValue);
        }

        // 重写hashCode方法
        @Override
        public int hashCode() {
            int result = key.hashCode();
            result = 31 * result + defaultValue.hashCode();
            return result;
        }
    }
}
