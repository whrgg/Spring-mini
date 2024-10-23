package com.itranswarp.beanpostprocesser;

import com.itranswarp.beanpostprocesser.OriginBean;

public class SecondProxyBean extends OriginBean {

    final OriginBean target;

    public SecondProxyBean(OriginBean target) {
        this.target = target;
    }

    @Override
    public void setVersion(String version) {
        target.setVersion(version);
    }

    @Override
    public String getName() {
        return target.getName();
    }

    @Override
    public String getVersion() {
        return target.getVersion();
    }
}
