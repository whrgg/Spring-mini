package springJdbc.jdbc.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited //允许注解在类上继承下去
public @interface Transactional {

    String value() default "platformTransactionManager";
}
