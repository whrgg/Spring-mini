package springAop.aop;

import springIoc.annotation.Bean;
import springIoc.annotation.ComponentScan;
import springIoc.annotation.Configuration;

@Configuration
@ComponentScan
public class AroundApplication {
    @Bean
    AroundProxyBeanPostProcessor createAroundProxyBeanPostProcessor() {
        return new AroundProxyBeanPostProcessor();
    }
}