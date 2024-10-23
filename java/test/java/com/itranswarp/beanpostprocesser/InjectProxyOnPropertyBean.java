package com.itranswarp.beanpostprocesser;


import springIoc.annotation.Autowired;
import springIoc.annotation.Component;
import springIoc.annotation.Order;

@Order(500)
@Component
public class InjectProxyOnPropertyBean {

    @Autowired
    public OriginBean injected;
}
