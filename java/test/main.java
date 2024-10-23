package test;

import springIoc.io.PropertyResolver;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class main {
    public static void main(String[] args) throws IOException {
        Properties props =new Properties();
        InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream("G:\\A.源码\\mini-spring\\src\\resource\\application.properties"));
//        InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream("G:\\A.源码\\mini-spring\\src\\resource\\application.yml"));
        props.load(inputStreamReader);
        PropertyResolver propertyResolver = new PropertyResolver(props);
        Integer property = propertyResolver.getProperty("${app.version}", int.class);
        System.out.println(property);

    }
}
