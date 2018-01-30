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
package com.alibaba.dubbo.registry.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.common.utils.UrlUtils;
import com.alibaba.dubbo.registry.NotifyListener;
import com.alibaba.dubbo.registry.Registry;
import com.alibaba.dubbo.registry.RegistryFactory;
import com.alibaba.dubbo.registry.RegistryService;
import com.alibaba.dubbo.rpc.Exporter;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;
import com.alibaba.dubbo.rpc.ProxyFactory;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.cluster.Cluster;
import com.alibaba.dubbo.rpc.cluster.Configurator;
import com.alibaba.dubbo.rpc.protocol.InvokerWrapper;

/**
 * RegistryProtocol
 * 
 * @author william.liangf
 * @author chao.liuc
 */
public class RegistryProtocol implements Protocol {

	private Cluster cluster;

	public void setCluster(Cluster cluster) {
		this.cluster = cluster;
	}

	private Protocol protocol;

	public void setProtocol(Protocol protocol) {
		this.protocol = protocol;
	}

	private RegistryFactory registryFactory;

	public void setRegistryFactory(RegistryFactory registryFactory) {
		this.registryFactory = registryFactory;
	}

	private ProxyFactory proxyFactory;

	public void setProxyFactory(ProxyFactory proxyFactory) {
		this.proxyFactory = proxyFactory;
	}

	public int getDefaultPort() {
		return 9090;
	}

	private static RegistryProtocol INSTANCE;

	public RegistryProtocol() {
		INSTANCE = this;
	}

	public static RegistryProtocol getRegistryProtocol() {
		if (INSTANCE == null) {
			ExtensionLoader.getExtensionLoader(Protocol.class).getExtension(Constants.REGISTRY_PROTOCOL); // load
		}
		return INSTANCE;
	}

	private final Map<URL, NotifyListener> overrideListeners = new ConcurrentHashMap<URL, NotifyListener>();

	public Map<URL, NotifyListener> getOverrideListeners() {
		return overrideListeners;
	}

	// 用于解决rmi重复暴露端口冲突的问题，已经暴露过的服务不再重新暴露
	// providerurl <--> exporter
	private final Map<String, ExporterChangeableWrapper<?>> bounds = new ConcurrentHashMap<String, ExporterChangeableWrapper<?>>();

	private final static Logger logger = LoggerFactory.getLogger(RegistryProtocol.class);

	/**
	 * 暴露服务(provider)
	 * 
	 * 将Invoker转换成对外的Exporter，缓存起来。
	 */
	public <T> Exporter<T> export(final Invoker<T> originInvoker) throws RpcException {

		/**
		 * <pre>
		 *  1、 export invoker
		 * 	暴露服务，这里就交给了具体的协议去暴露服务(例如DubboProtocol。nettyServer启动监听，到这里provider可以提供服务了)
		 * originInvoker-->registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D65388%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496900761500%26version%3D1.0.0&pid=65388&registry=zookeeper&timestamp=1496900761339
		 * exporter-->com.alibaba.dubbo.registry.integration.RegistryProtocol$ExporterChangeableWrapper@5ed6b80d(com.alibaba.dubbo.rpc.listener.ListenerExporterWrapper@68a6ff9)
		 */
		final ExporterChangeableWrapper<T> exporter = doLocalExport(originInvoker);

		/**
		 * <pre>
		 * 2、registry provider
		 * 	 根据invoker中的url获取Registry实例， 并且连接到注册中心
		 * registry-->zookeeper://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&interface=com.alibaba.dubbo.registry.RegistryService&pid=68260&timestamp=1496905025327
		 */
		final Registry registry = getRegistry(originInvoker);

		/**
		 * <pre>
		 * 3、获取将要注册到注册中心的URL
		 * registedProviderUrl-->dubbo://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=68260&revision=1.0.0&side=provider&timestamp=1496905026515&version=1.0.0
		 */
		final URL registedProviderUrl = getRegistedProviderUrl(originInvoker);

		/**
		 * <pre>
		 * 4、调用远端注册中心的register方法进行服务注册（将 提供者的url注册到注册中心,即在dubbo节点下面创建提供者的节点）,
		 * 	 若有消费者订阅此服务，则推送消息让消费者引用此服务,注册中心缓存了所有提供者注册的服务以供消费者发现。
		 * registry-->FailbackRegistry
		 */
		registry.register(registedProviderUrl);

		/**
		 * <pre>
		 * 5、设置listener
		 * registedProviderUrl-->dubbo://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=68260&revision=1.0.0&side=provider&timestamp=1496905026515&version=1.0.0
		 * overrideSubscribeUrl-->provider://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&category=configurators&check=false&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=68260&revision=1.0.0&side=provider&timestamp=1496905026515&version=1.0.0
		 */
		final URL overrideSubscribeUrl = getSubscribedOverrideUrl(registedProviderUrl);
		final OverrideListener overrideSubscribeListener = new OverrideListener(overrideSubscribeUrl);
		overrideListeners.put(overrideSubscribeUrl, overrideSubscribeListener);

		/**
		 * <pre>
		 *  6、订阅服务(判断provider的url是否发生变化，如果有变化，会重新暴露服务),提供者向注册中心订阅所有注册服务的覆盖配置，
		 * 当注册中心有此服务的覆盖配置注册进来时，推送消息给提供者，让它重新暴露服务，这由管理页面完成。
		 * 
		 * 此处逻辑很多！！！
		 */
		registry.subscribe(overrideSubscribeUrl, overrideSubscribeListener);

		/**
		 * <pre>
		 * 7、保证每次export都返回一个新的exporter实例
		 */
		return new Exporter<T>() {
			public Invoker<T> getInvoker() {
				return exporter.getInvoker();// 返回的是：InvokerDelegete
			}

			public void unexport() {
				try {
					exporter.unexport();
				} catch (Throwable t) {
					logger.warn(t.getMessage(), t);
				}
				try {
					registry.unregister(registedProviderUrl);
				} catch (Throwable t) {
					logger.warn(t.getMessage(), t);
				}
				try {
					overrideListeners.remove(overrideSubscribeUrl);
					registry.unsubscribe(overrideSubscribeUrl, overrideSubscribeListener);
				} catch (Throwable t) {
					logger.warn(t.getMessage(), t);
				}
			}
		};
	}

	// 交给具体的协议进行服务暴露（例如DubboProtocol）
	@SuppressWarnings("unchecked")
	private <T> ExporterChangeableWrapper<T> doLocalExport(final Invoker<T> originInvoker) {

		/**
		 * <pre>
		 * originInvoker -->registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D65388%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496900761500%26version%3D1.0.0&pid=65388&registry=zookeeper&timestamp=1496900761339
		 * key --> dubbo://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=65388&revision=1.0.0&side=provider&timestamp=1496900761500&version=1.0.0
		 */
		String key = getCacheKey(originInvoker);
		ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
		if (exporter == null) {
			synchronized (bounds) {
				exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
				if (exporter == null) {
					final Invoker<?> invokerDelegete = new InvokerDelegete<T>(originInvoker, getProviderUrl(originInvoker));
					// 具体的协议暴露服务
					// 此处protocol还是最上面生成的代码，调用代码中的export方法，会根据协议名选择调用具体的实现类
					// 这里我们需要调用DubboProtocol的export方法
					// 这里的invoker在上一步做了修改
					// protocol=> ProtocolFilterWrapper->ProtocolListenerWrapper -> DubboProtocol
					exporter = new ExporterChangeableWrapper<T>((Exporter<T>) protocol.export(invokerDelegete), originInvoker);
					bounds.put(key, exporter);
				}
			}
		}
		return (ExporterChangeableWrapper<T>) exporter;
	}

	/**
	 * 对修改了url的invoker重新export
	 * 
	 * @param originInvoker
	 * @param newInvokerUrl
	 */
	@SuppressWarnings("unchecked")
	private <T> void doChangeLocalExport(final Invoker<T> originInvoker, URL newInvokerUrl) {
		String key = getCacheKey(originInvoker);
		final ExporterChangeableWrapper<T> exporter = (ExporterChangeableWrapper<T>) bounds.get(key);
		if (exporter == null) {
			logger.warn(new IllegalStateException("error state, exporter should not be null"));
			return;// 不存在是异常场景 直接返回
		} else {
			final Invoker<T> invokerDelegete = new InvokerDelegete<T>(originInvoker, newInvokerUrl);
			exporter.setExporter(protocol.export(invokerDelegete));
		}
	}

	/**
	 * 根据invoker的地址获取registry实例
	 * 
	 * @param originInvoker
	 * @return
	 */
	private Registry getRegistry(final Invoker<?> originInvoker) {
		// registryUrl-->registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D68260%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496905026515%26version%3D1.0.0&pid=68260&registry=zookeeper&timestamp=1496905025327
		URL registryUrl = originInvoker.getUrl();
		if (Constants.REGISTRY_PROTOCOL.equals(registryUrl.getProtocol())) {
			// protocol-->zookeeper
			String protocol = registryUrl.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_DIRECTORY);
			registryUrl = registryUrl.setProtocol(protocol).removeParameter(Constants.REGISTRY_KEY);
		}
		// registryFactory-->AbstractRegistryFactory
		// registryUrl -->
		// zookeeper://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D68260%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496905026515%26version%3D1.0.0&pid=68260&timestamp=1496905025327
		return registryFactory.getRegistry(registryUrl);
	}

	/**
	 * 返回注册到注册中心的URL，对URL参数进行一次过滤
	 * 
	 * @param originInvoker
	 * @return
	 */
	private URL getRegistedProviderUrl(final Invoker<?> originInvoker) {
		URL providerUrl = getProviderUrl(originInvoker);// 提供者url
		// 注册中心看到的地址
		final URL registedProviderUrl = providerUrl.removeParameters(getFilteredKeys(providerUrl)).removeParameter(Constants.MONITOR_KEY);
		return registedProviderUrl;
	}

	private URL getSubscribedOverrideUrl(URL registedProviderUrl) {
		return registedProviderUrl.setProtocol(Constants.PROVIDER_PROTOCOL).addParameters(Constants.CATEGORY_KEY, Constants.CONFIGURATORS_CATEGORY, Constants.CHECK_KEY, String.valueOf(false));
	}

	/**
	 * 通过invoker的url 获取 providerUrl的地址
	 * 
	 * @param origininvoker
	 * @return
	 */
	private URL getProviderUrl(final Invoker<?> origininvoker) {
		// origininvoker-->registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D65388%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496900761500%26version%3D1.0.0&pid=65388&registry=zookeeper&timestamp=1496900761339
		// 提供者url：
		// export-->dubbo://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=65388&revision=1.0.0&side=provider&timestamp=1496900761500&version=1.0.0
		String export = origininvoker.getUrl().getParameterAndDecoded(Constants.EXPORT_KEY);
		if (export == null || export.length() == 0) {
			throw new IllegalArgumentException("The registry export url is null! registry: " + origininvoker.getUrl());
		}

		URL providerUrl = URL.valueOf(export);
		return providerUrl;
	}

	/**
	 * 获取invoker在bounds中缓存的key
	 * 
	 * @param originInvoker
	 * @return
	 */
	private String getCacheKey(final Invoker<?> originInvoker) {
		URL providerUrl = getProviderUrl(originInvoker);
		String key = providerUrl.removeParameters("dynamic", "enabled").toFullString();
		return key;
	}

	/**
	 * 引用远程服务（consumer）
	 */
	@SuppressWarnings("unchecked")
	public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
		// url
		// -->registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&dubbo=2.0.0&pid=48320&refer=application%3Dhello_consumer%26check%3Dfalse%26dubbo%3D2.0.0%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26loadbalance%3Drandom%26methods%3DgetUserById%2CaddUser%2CsayHello%26mock%3Dtrue%26pid%3D48320%26retries%3D5%26revision%3D1.0.0%26side%3Dconsumer%26timeout%3D15000%26timestamp%3D1497094162302%26version%3D1.0.0&registry=zookeeper&timestamp=1497094184777
		url = url.setProtocol(url.getParameter(Constants.REGISTRY_KEY, Constants.DEFAULT_REGISTRY)).removeParameter(Constants.REGISTRY_KEY);

		// 1、获取到注册中心的连接，并返回连接实例
		// url
		// -->zookeeper://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_consumer&dubbo=2.0.0&pid=48320&refer=application%3Dhello_consumer%26check%3Dfalse%26dubbo%3D2.0.0%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26loadbalance%3Drandom%26methods%3DgetUserById%2CaddUser%2CsayHello%26mock%3Dtrue%26pid%3D48320%26retries%3D5%26revision%3D1.0.0%26side%3Dconsumer%26timeout%3D15000%26timestamp%3D1497094162302%26version%3D1.0.0&timestamp=1497094184777
		Registry registry = registryFactory.getRegistry(url);
		if (RegistryService.class.equals(type)) {
			return proxyFactory.getInvoker((T) registry, type, url);
		}

		// group="a,b" or group="*"
		Map<String, String> qs = StringUtils.parseQueryString(url.getParameterAndDecoded(Constants.REFER_KEY));
		String group = qs.get(Constants.GROUP_KEY);
		if (group != null && group.length() > 0) {
			if ((Constants.COMMA_SPLIT_PATTERN.split(group)).length > 1 || "*".equals(group)) {
				return doRefer(getMergeableCluster(), registry, type, url);
			}
		}
		return doRefer(cluster, registry, type, url);
	}

	private Cluster getMergeableCluster() {
		return ExtensionLoader.getExtensionLoader(Cluster.class).getExtension("mergeable");
	}

	// 消费者流程
	private <T> Invoker<T> doRefer(Cluster cluster, Registry registry, Class<T> type, URL url) {
		RegistryDirectory<T> directory = new RegistryDirectory<T>(type, url);
		directory.setRegistry(registry);
		directory.setProtocol(protocol);
		// 2、获取消费者url
		// subscribeUrl-->consumer://192.168.2.9/cn.com.sky.dubbo.server.service.DemoService?application=hello_consumer&check=false&dubbo=2.0.0&interface=cn.com.sky.dubbo.server.service.DemoService&loadbalance=random&methods=addUser,getUserById,sayHello&mock=true&pid=44364&retries=5&revision=1.0.0&side=consumer&timeout=1500000&timestamp=1497097889171&version=1.0.0
		URL subscribeUrl = new URL(Constants.CONSUMER_PROTOCOL, NetUtils.getLocalHost(), 0, type.getName(), directory.getUrl().getParameters());
		if (!Constants.ANY_VALUE.equals(url.getServiceInterface()) && url.getParameter(Constants.REGISTER_KEY, true)) {
			// 3、消费者注册到注册中心
			registry.register(subscribeUrl.addParameters(Constants.CATEGORY_KEY, Constants.CONSUMERS_CATEGORY, Constants.CHECK_KEY, String.valueOf(false)));
		}
		/**
		 * <pre>
		 * 4、消费者订阅(流程很多，逻辑很复杂)
		 * 订阅该service下的 providers,configurators,routers
		 * consumer订阅该服务下的providers/routers/configurators
		 * 
		 * consumer://10.69.58.75/cn.com.sky.dubbo.server.service.DemoService?application=hello_consumer&category=providers,configurators,routers&check=false&dubbo=2.0.0&interface=cn.com.sky.dubbo.server.service.DemoService&loadbalance=random&methods=getUserById,addUser,sayHello&pid=27020&retries=5&revision=1.0.0&side=consumer&timeout=1500000&timestamp=1504765339610&version=1.0.0
		 */
		URL subUrl=subscribeUrl.addParameter(Constants.CATEGORY_KEY, Constants.PROVIDERS_CATEGORY + "," + Constants.CONFIGURATORS_CATEGORY + "," + Constants.ROUTERS_CATEGORY);
		directory.subscribe(subUrl);
		// 5、加入到集群
		// clustr-->MockClusterWrapper
		// return MockClusterInvoker
		return cluster.join(directory);// com.alibaba.dubbo.registry.integration.RegistryDirectory@601ae9d
	}

	// 过滤URL中不需要输出的参数(以点号开头的)
	private static String[] getFilteredKeys(URL url) {
		Map<String, String> params = url.getParameters();
		if (params != null && !params.isEmpty()) {
			List<String> filteredKeys = new ArrayList<String>();
			for (Map.Entry<String, String> entry : params.entrySet()) {
				if (entry != null && entry.getKey() != null && entry.getKey().startsWith(Constants.HIDE_KEY_PREFIX)) {
					filteredKeys.add(entry.getKey());
				}
			}
			return filteredKeys.toArray(new String[filteredKeys.size()]);
		} else {
			return new String[] {};
		}
	}

	public void destroy() {
		List<Exporter<?>> exporters = new ArrayList<Exporter<?>>(bounds.values());
		for (Exporter<?> exporter : exporters) {
			exporter.unexport();
		}
		bounds.clear();
	}

	/**
	 * <pre>
	 * 重新export 
	 * 1.protocol中的exporter destory问题1.要求registryprotocol返回的exporter可以正常destroy
	 * 2.notify后不需要重新向注册中心注册3.export 方法传入的invoker最好能一直作为exporter的invoker.
	 */
	private class OverrideListener implements NotifyListener {

		private volatile List<Configurator> configurators;

		private final URL subscribeUrl;

		public OverrideListener(URL subscribeUrl) {
			this.subscribeUrl = subscribeUrl;
		}

		/**
		 * <pre>
		 * provider 端可识别的override url只有这两种. 
		 * override://0.0.0.0/serviceName?timeout=10
		 * override://0.0.0.0/?timeout=10
		 * 
		 * 
		 * urls -->[empty://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&category=configurators&check=false&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=68260&revision=1.0.0&side=provider&timestamp=1496905026515&version=1.0.0]
		 * 
		 * 生产者provider的notify
		 */
		public void notify(List<URL> urls) {
			// 1、从urls中移除不符合覆盖条件的覆盖url
			List<URL> result = null;
			for (URL url : urls) {
				URL overrideUrl = url;
				if (url.getParameter(Constants.CATEGORY_KEY) == null && Constants.OVERRIDE_PROTOCOL.equals(url.getProtocol())) {
					// 兼容旧版本
					overrideUrl = url.addParameter(Constants.CATEGORY_KEY, Constants.CONFIGURATORS_CATEGORY);
				}

				if (!UrlUtils.isMatch(subscribeUrl, overrideUrl)) {
					if (result == null) {
						result = new ArrayList<URL>(urls);
					}
					result.remove(url);
					logger.warn("Subsribe category=configurator, but notifed non-configurator urls. may be registry bug. unexcepted url: " + url);
				}
			}
			if (result != null) {
				urls = result;
			}
			// 2、将覆盖urls转换为configurators
			this.configurators = RegistryDirectory.toConfigurators(urls);

			// 3、获取现在已经暴露的url，如果覆盖url和现有url不一致，则重新暴露服务。
			List<ExporterChangeableWrapper<?>> exporters = new ArrayList<ExporterChangeableWrapper<?>>(bounds.values());
			for (ExporterChangeableWrapper<?> exporter : exporters) {
				/**
				 * <pre>
				 * a、获取已暴露的invoker
				 * invoker -->registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D68260%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496905026515%26version%3D1.0.0&pid=68260&registry=zookeeper&timestamp=1496905025327
				 */
				Invoker<?> invoker = exporter.getOriginInvoker();
				final Invoker<?> originInvoker;
				if (invoker instanceof InvokerDelegete) {
					originInvoker = ((InvokerDelegete<?>) invoker).getInvoker();
				} else {
					originInvoker = invoker;
				}
				/**
				 * <pre>
				 * b、获取已暴露的url
				 * originUrl --> dubbo://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=68260&revision=1.0.0&side=provider&timestamp=1496905026515&version=1.0.0
				 */
				URL originUrl = RegistryProtocol.this.getProviderUrl(originInvoker);
				// c、获取新的url
				URL newUrl = getNewInvokerUrl(originUrl, urls);

				// d、如果修改了url，需要重新export。
				if (!originUrl.equals(newUrl)) {
					RegistryProtocol.this.doChangeLocalExport(originInvoker, newUrl);
				}
			}
		}

		private URL getNewInvokerUrl(URL url, List<URL> urls) {
			List<Configurator> localConfigurators = this.configurators; // local reference
			// 合并override参数
			if (localConfigurators != null && localConfigurators.size() > 0) {
				for (Configurator configurator : localConfigurators) {
					url = configurator.configure(url);
				}
			}
			return url;
		}
	}

	public static class InvokerDelegete<T> extends InvokerWrapper<T> {
		private final Invoker<T> invoker;

		/**
		 * @param invoker
		 * @param url
		 *            invoker.getUrl返回此值
		 */
		public InvokerDelegete(Invoker<T> invoker, URL url) {
			// invoker -->
			// registry://127.0.0.1:2181/com.alibaba.dubbo.registry.RegistryService?application=hello_provider&dubbo=2.0.0&export=dubbo%3A%2F%2F10.69.61.196%3A20880%2Fcn.com.sky.dubbo.server.service.DemoService%3Fanyhost%3Dtrue%26application%3Dhello_provider%26dubbo%3D2.0.0%26generic%3Dfalse%26interface%3Dcn.com.sky.dubbo.server.service.DemoService%26methods%3DaddUser%2CgetUserById%2CsayHello%26pid%3D137728%26revision%3D1.0.0%26side%3Dprovider%26timestamp%3D1496992201705%26version%3D1.0.0&pid=137728&registry=zookeeper&timestamp=1496991644290
			// url -->
			// dubbo://10.69.61.196:20880/cn.com.sky.dubbo.server.service.DemoService?anyhost=true&application=hello_provider&dubbo=2.0.0&generic=false&interface=cn.com.sky.dubbo.server.service.DemoService&methods=addUser,getUserById,sayHello&pid=137728&revision=1.0.0&side=provider&timestamp=1496992201705&version=1.0.0
			super(invoker, url);
			this.invoker = invoker;
		}

		public Invoker<T> getInvoker() {
			if (invoker instanceof InvokerDelegete) {
				return ((InvokerDelegete<T>) invoker).getInvoker();
			} else {
				return invoker;
			}
		}
	}

	/**
	 * exporter代理,建立返回的exporter与protocol export出的exporter的对应关系，在override时可以进行关系修改.
	 * 
	 * @author chao.liuc
	 *
	 * @param <T>
	 */
	private class ExporterChangeableWrapper<T> implements Exporter<T> {

		private Exporter<T> exporter;

		private final Invoker<T> originInvoker;

		public ExporterChangeableWrapper(Exporter<T> exporter, Invoker<T> originInvoker) {
			this.exporter = exporter;
			this.originInvoker = originInvoker;
		}

		public Invoker<T> getOriginInvoker() {
			return originInvoker;
		}

		public Invoker<T> getInvoker() {
			return exporter.getInvoker();
		}

		public void setExporter(Exporter<T> exporter) {
			this.exporter = exporter;
		}

		public void unexport() {
			String key = getCacheKey(this.originInvoker);
			bounds.remove(key);
			exporter.unexport();
		}
	}
}