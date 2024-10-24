package springJdbc.jdbc.transactional;

import springAop.aop.AnnotationProxyBeanPostProcessor;
import springIoc.context.BeanPostProcessor;
import springJdbc.jdbc.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

public class TransactionalBeanPostProcessor extends AnnotationProxyBeanPostProcessor<Transactional> {

}
