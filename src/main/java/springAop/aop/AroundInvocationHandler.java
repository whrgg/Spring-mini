package springAop.aop;

import springAop.annotation.Polite;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class AroundInvocationHandler implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if(method.getAnnotation(Polite.class)!=null){
            String ret = String.valueOf(method.invoke(proxy, args));
            if (ret.endsWith(".")) {
                ret = ret.substring(0, ret.length() - 1) + "!";
            }
            return ret;
        }

        return method.invoke(proxy, args);
    }
}
