/*
 * Copyright 1999-2012 Alibaba Group.
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
package com.alibaba.dubbo.common.extension.factory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.ExtensionFactory;
import com.alibaba.dubbo.common.extension.ExtensionLoader;

/**
 * AdaptiveExtensionFactory
 * 
 * @author william.liangf
 * 
 *         默认的ExtensionFactory实现中，AdaptiveExtensionFactotry被@Adaptive注解注释，
 *         也就是它就是ExtensionFactory对应的自适应扩展实现
 *         (每个扩展点最多只能有一个自适应实现，如果所有实现中没有被@Adaptive注释的，那么dubbo会动态生成一个自适应实现类
 *         )，也就是说，所有对ExtensionFactory调用的地方，实际上调用的都是AdpativeExtensionFactory
 * 
 */
@Adaptive
public class AdaptiveExtensionFactory implements ExtensionFactory {

	private final List<ExtensionFactory> factories;

	public AdaptiveExtensionFactory() {
		ExtensionLoader<ExtensionFactory> loader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
		List<ExtensionFactory> list = new ArrayList<ExtensionFactory>();
		// [spi,spring]
		for (String name : loader.getSupportedExtensions()) {
			list.add(loader.getExtension(name)); // 将所有ExtensionFactory实现保存起来.
		}
		factories = Collections.unmodifiableList(list);
	}

	public <T> T getExtension(Class<T> type, String name) {
		// 依次遍历各个ExtensionFactory实现的getExtension方法，一旦获取到Extension即返回
		// 如果遍历完所有的ExtensionFactory实现均无法找到Extension,则返回null
		for (ExtensionFactory factory : factories) {
			T extension = factory.getExtension(type, name);
			if (extension != null) {
				return extension;
			}
		}
		return null;
	}

}
