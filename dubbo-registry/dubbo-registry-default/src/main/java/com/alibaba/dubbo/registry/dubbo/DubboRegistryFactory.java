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
package com.alibaba.dubbo.registry.dubbo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.registry.Registry;
import com.alibaba.dubbo.registry.RegistryService;
import com.alibaba.dubbo.registry.integration.RegistryDirectory;
import com.alibaba.dubbo.registry.support.AbstractRegistryFactory;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.cluster.Cluster;

/**
 * DubboRegistryFactory
 * 
 * @author william.liangf
 */
public class DubboRegistryFactory extends AbstractRegistryFactory {

	private Protocol protocol;

	public void setProtocol(Protocol protocol) {
		this.protocol = protocol;
	}

	private ProxyFactory proxyFactory;

	public void setProxyFactory(ProxyFactory proxyFactory) {
		this.proxyFactory = proxyFactory;
	}

	private Cluster cluster;

	public void setCluster(Cluster cluster) {
		this.cluster = cluster;
	}

	public Registry createRegistry(URL url) {
		// url-->
		// dubbo://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&callbacks=10000&connect.timeout=10000&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&lazy=true&methods=register,subscribe,unregister,unsubscribe,lookup&pid=48320&reconnect=false&sticky=true&subscribe.1.callback=true&timeout=10000&timestamp=1497094184777&unsubscribe.1.callback=false
		url = getRegistryURL(url);
		List<URL> urls = new ArrayList<URL>();
		urls.add(url.removeParameter(Constants.BACKUP_KEY));
		String backup = url.getParameter(Constants.BACKUP_KEY);
		if (backup != null && backup.length() > 0) {
			String[] addresses = Constants.COMMA_SPLIT_PATTERN.split(backup);
			for (String address : addresses) {
				urls.add(url.setAddress(address));
			}
		}
		// consumerUrl -->
		// dubbo://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&callbacks=10000&connect.timeout=10000&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&lazy=true&methods=register,subscribe,unregister,unsubscribe,lookup&pid=48320&reconnect=false&refer=application%3Dhello_consumer%26callbacks%3D10000%26connect.timeout%3D10000%26dubbo%3D2.0.0%26interface%3Dcom.alibaba.dubbo.registry.RegistryService%26lazy%3Dtrue%26methods%3Dregister%2Csubscribe%2Cunregister%2Cunsubscribe%2Clookup%26pid%3D48320%26reconnect%3Dfalse%26sticky%3Dtrue%26subscribe.1.callback%3Dtrue%26timeout%3D10000%26timestamp%3D1497094184777%26unsubscribe.1.callback%3Dfalse&sticky=true&subscribe.1.callback=true&timeout=10000&timestamp=1497094184777&unsubscribe.1.callback=false
		// directoryUrl-->
		// dubbo://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&callbacks=10000&connect.timeout=10000&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&lazy=true&methods=register,subscribe,unregister,unsubscribe,lookup&pid=48320&reconnect=false&sticky=true&subscribe.1.callback=true&timeout=10000&timestamp=1497094184777&unsubscribe.1.callback=false
		RegistryDirectory<RegistryService> directory = new RegistryDirectory<RegistryService>(RegistryService.class, url.addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName())
				.addParameterAndEncoded(Constants.REFER_KEY, url.toParameterString()));

		// registryInvoker-->MockClusterInvoker
		// cluster-->FailoverCluster
		Invoker<RegistryService> registryInvoker = cluster.join(directory);
		// proxyFactory-->JavasisstProxy->Stub
		RegistryService registryService = proxyFactory.getProxy(registryInvoker);
		DubboRegistry registry = new DubboRegistry(registryInvoker, registryService);
		directory.setRegistry(registry);
		directory.setProtocol(protocol);
		// notify逻辑？？
		// urls-->[dubbo://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&callbacks=10000&connect.timeout=10000&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&lazy=true&methods=register,subscribe,unregister,unsubscribe,lookup&pid=48320&reconnect=false&sticky=true&subscribe.1.callback=true&timeout=10000&timestamp=1497094184777&unsubscribe.1.callback=false]
		directory.notify(urls);
		// 订阅
		directory.subscribe(new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, RegistryService.class.getName(), url.getParameters()));
		return registry;
	}

	private static URL getRegistryURL(URL url) {
		return url.setPath(RegistryService.class.getName()).removeParameter(Constants.EXPORT_KEY).removeParameter(Constants.REFER_KEY)
				.addParameter(Constants.INTERFACE_KEY, RegistryService.class.getName()).addParameter(Constants.CLUSTER_STICKY_KEY, "true").addParameter(Constants.LAZY_CONNECT_KEY, "true")
				.addParameter(Constants.RECONNECT_KEY, "false").addParameterIfAbsent(Constants.TIMEOUT_KEY, "10000").addParameterIfAbsent(Constants.CALLBACK_INSTANCES_LIMIT_KEY, "10000")
				.addParameterIfAbsent(Constants.CONNECT_TIMEOUT_KEY, "10000")
				.addParameter(Constants.METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(Wrapper.getWrapper(RegistryService.class).getDeclaredMethodNames())), ","))
				// .addParameter(Constants.STUB_KEY, RegistryServiceStub.class.getName())
				// .addParameter(Constants.STUB_EVENT_KEY, Boolean.TRUE.toString()) //for event
				// dispatch
				// .addParameter(Constants.ON_DISCONNECT_KEY, "disconnect")
				.addParameter("subscribe.1.callback", "true").addParameter("unsubscribe.1.callback", "false");
	}
}