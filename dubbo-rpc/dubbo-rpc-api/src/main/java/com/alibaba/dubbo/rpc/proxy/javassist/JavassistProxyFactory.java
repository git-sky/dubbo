/*
 * Copyright 1999-2011 Alibaba Group.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.rpc.proxy.javassist;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.bytecode.Proxy;
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.proxy.AbstractProxyFactory;
import com.alibaba.dubbo.rpc.proxy.AbstractProxyInvoker;
import com.alibaba.dubbo.rpc.proxy.InvokerInvocationHandler;

/**
 * JavaassistRpcProxyFactory 

 * @author william.liangf
 */
public class JavassistProxyFactory extends AbstractProxyFactory {

	/**
	 * 消费者获取动态代理
	 */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }

    /**
     * 生产者生成Invoker
     */
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper类不能正确处理带$的类名
    	//proxy --> cn.com.sky.dubbo.server.service.impl.DemoServiceImpl@457fab06
    	//type --> interface cn.com.sky.dubbo.server.service.DemoService
    	//url --> injvm://127.0.0.1/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=65388&revision=1.0.0&side=provider&timestamp=1496900761500&version=1.0.0
        //url:  registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D65388%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496900761500%26version%3D1.0.0&pid=65388&registry=zookeeper&timestamp=1496900761339
    	final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName, 
                                      Class<?>[] parameterTypes, 
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments);
            }
        };
    }

}