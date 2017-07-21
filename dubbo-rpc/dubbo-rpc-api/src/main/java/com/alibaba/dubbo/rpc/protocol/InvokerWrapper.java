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
package com.alibaba.dubbo.rpc.protocol;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcException;

/**
 * InvokerWrapper
 * 
 * @author william.liangf
 */
public class InvokerWrapper<T> implements Invoker<T> {
    
    private final Invoker<T> invoker;

    private final URL url;

    public InvokerWrapper(Invoker<T> invoker, URL url){
        this.invoker = invoker;
        //url-->dubbo://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&callbacks=10000&check=false&connect.timeout=10000&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&lazy=true&methods=register,subscribe,unregister,unsubscribe,lookup&pid=48320&reconnect=false&sticky=true&subscribe.1.callback=true&timeout=10000&timestamp=1497094184777&unsubscribe.1.callback=false
        this.url = url;
    }

    public Class<T> getInterface() {
        return invoker.getInterface();
    }

    public URL getUrl() {
        return url;
    }

    public boolean isAvailable() {
        return invoker.isAvailable();
    }

    public Result invoke(Invocation invocation) throws RpcException {
        return invoker.invoke(invocation);
    }

    public void destroy() {
        invoker.destroy();
    }

}