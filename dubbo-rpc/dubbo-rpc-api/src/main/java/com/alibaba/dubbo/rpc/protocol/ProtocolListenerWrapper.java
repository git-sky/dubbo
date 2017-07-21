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

import java.util.Collections;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.ExporterListener;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.InvokerListener;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.listener.ListenerExporterWrapper;
import com.alibaba.dubbo.rpc.listener.ListenerInvokerWrapper;

/**
 * ListenerProtocol
 * 
 * @author william.liangf
 */
public class ProtocolListenerWrapper implements Protocol {

	private final Protocol protocol;

	public ProtocolListenerWrapper(Protocol protocol) {
		if (protocol == null) {
			throw new IllegalArgumentException("protocol == null");
		}
		this.protocol = protocol;
	}

	public int getDefaultPort() {
		return protocol.getDefaultPort();
	}

	/**
     * 
     */
	public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
		if (Constants.REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
			// protocol-->com.alibaba.dubbo.registry.integration.RegistryProtocol@5dc4d0d2
			return protocol.export(invoker);
		}
		// protocol-->com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol@35799785
		return new ListenerExporterWrapper<T>(protocol.export(invoker), Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(ExporterListener.class).getActivateExtension(invoker.getUrl(),
				Constants.EXPORTER_LISTENER_KEY)));
	}

	public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
		if (Constants.REGISTRY_PROTOCOL.equals(url.getProtocol())) {
			//protocol-->com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper@7f95a585(com.alibaba.dubbo.registry.integration.RegistryProtocol@99a6a17)
			//protocol-->com.alibaba.dubbo.registry.integration.RegistryProtocol@7e6a6034
			// url --> registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&dubbo=2.0.0&pid=48320&refer=application%3Dhello_consumer%26check%3Dfalse%26dubbo%3D2.0.0%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26loadbalance%3Drandom%26methods%3DgetUserById%2CaddUser%2CsayHello%26mock%3Dtrue%26pid%3D48320%26retries%3D5%26revision%3D1.0.0%26side%3Dconsumer%26timeout%3D15000%26timestamp%3D1497094162302%26version%3D1.0.0&registry=zookeeper&timestamp=1497094184777
			return protocol.refer(type, url);
		}
		//protocol-->com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper@21055e25(com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol@5b2ef33)
		//protocol-->com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol@30fe5ac
		return new ListenerInvokerWrapper<T>(protocol.refer(type, url), Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(InvokerListener.class).getActivateExtension(url,
				Constants.INVOKER_LISTENER_KEY)));
	}

	public void destroy() {
		protocol.destroy();
	}

}