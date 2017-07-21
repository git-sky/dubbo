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
package com.alibaba.dubbo.registry.zookeeper;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.registry.Registry;
import com.alibaba.dubbo.registry.support.AbstractRegistryFactory;
import com.alibaba.dubbo.remoting.zookeeper.ZookeeperTransporter;

/**
 * ZookeeperRegistryFactory.
 * 
 * @author william.liangf
 */
public class ZookeeperRegistryFactory extends AbstractRegistryFactory {

	private ZookeeperTransporter zookeeperTransporter;

	public void setZookeeperTransporter(ZookeeperTransporter zookeeperTransporter) {
		this.zookeeperTransporter = zookeeperTransporter;
	}

	/**
	 * 连接到zookeeper注册中心,并返回注册实例。
	 */
	public Registry createRegistry(URL url) {
		//url(provider) --> zookeeper://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&pid=68260&timestamp=1496905025327
		//url(consumer) --> zookeeper://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&pid=42708&timestamp=1497097421948
		return new ZookeeperRegistry(url, zookeeperTransporter);
	}

}