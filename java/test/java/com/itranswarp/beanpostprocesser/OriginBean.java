package com.itranswarp.beanpostprocesser;

import springIoc.annotation.Component;
import springIoc.annotation.Order;
import springIoc.annotation.Value;

@Order(300)
@Component
public class OriginBean {

    @Value("${app.title}")
    public String name;

    public String version;

    @Value("${app.version}")
    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return this.version;
    }
}
