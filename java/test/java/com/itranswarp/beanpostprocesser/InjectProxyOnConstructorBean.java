package com.itranswarp.beanpostprocesser;


import springIoc.annotation.Autowired;
import springIoc.annotation.Component;

@Component
public class InjectProxyOnConstructorBean {

    public final OriginBean injected;

    public InjectProxyOnConstructorBean(@Autowired OriginBean injected) {
        this.injected = injected;
    }
}
