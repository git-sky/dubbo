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
package com.alibaba.dubbo.rpc.protocol.dubbo;

import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.AtomicPositiveInteger;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.TimeoutException;
import com.alibaba.dubbo.remoting.exchange.ExchangeClient;
import com.alibaba.dubbo.remoting.exchange.ResponseFuture;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.protocol.AbstractInvoker;
import com.alibaba.dubbo.rpc.support.RpcUtils;

/**
 * DubboInvoker
 * 
 * @author william.liangf
 * @author chao.liuc
 */
public class DubboInvoker<T> extends AbstractInvoker<T> {

	private final ExchangeClient[] clients;

	private final AtomicPositiveInteger index = new AtomicPositiveInteger();

	private final String version;

	private final ReentrantLock destroyLock = new ReentrantLock();

	private final Set<Invoker<?>> invokers;

	public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients) {
		this(serviceType, url, clients, null);
	}

	public DubboInvoker(Class<T> serviceType, URL url, ExchangeClient[] clients, Set<Invoker<?>> invokers) {
		super(serviceType, url, new String[] { Constants.INTERFACE_KEY, Constants.GROUP_KEY, Constants.TOKEN_KEY, Constants.TIMEOUT_KEY });
		this.clients = clients;
		// get version.
		this.version = url.getParameter(Constants.VERSION_KEY, "0.0.0");
		this.invokers = invokers;
	}

	/**
	 * 真正执行调用的地方(网络调用)
	 * 
	 * SKY_COMMENT
	 */
	@Override
	protected Result doInvoke(final Invocation invocation) throws Throwable {
		RpcInvocation inv = (RpcInvocation) invocation;
		final String methodName = RpcUtils.getMethodName(invocation);

		// 1、继续给invocation添加数据
		inv.setAttachment(Constants.PATH_KEY, getUrl().getPath());
		inv.setAttachment(Constants.VERSION_KEY, version);

		// 2、获取client链接
		// 确定此次调用该使用哪个client（一个client代表一个connection）
		ExchangeClient currentClient;
		if (clients.length == 1) {
			currentClient = clients[0];
		} else {
			currentClient = clients[index.getAndIncrement() % clients.length];// 轮询
		}

		// 3、执行网络调用(分三种)
		try {
			boolean isAsync = RpcUtils.isAsync(getUrl(), invocation);
			boolean isOneway = RpcUtils.isOneway(getUrl(), invocation);
			int timeout = getUrl().getMethodParameter(methodName, Constants.TIMEOUT_KEY, Constants.DEFAULT_TIMEOUT);// 超时时间，默认1秒
			if (isOneway) {// a.单向调用
				boolean isSent = getUrl().getMethodParameter(methodName, Constants.SENT_KEY, false);
				// 单向调用只负责发送消息，不等待服务端应答，所以没有返回值
				currentClient.send(inv, isSent);
				RpcContext.getContext().setFuture(null);
				return new RpcResult();
			} else if (isAsync) {// b.异步调用 HeaderExchangeChannel
				ResponseFuture future = currentClient.request(inv, timeout);
				RpcContext.getContext().setFuture(new FutureAdapter<Object>(future));
				return new RpcResult();
			} else {// c.同步调用
				RpcContext.getContext().setFuture(null);
				return (Result) currentClient.request(inv, timeout).get();
			}
		} catch (TimeoutException e) {
			throw new RpcException(RpcException.TIMEOUT_EXCEPTION, "Invoke remote method timeout. method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: "
					+ e.getMessage(), e);
		} catch (RemotingException e) {
			throw new RpcException(RpcException.NETWORK_EXCEPTION, "Failed to invoke remote method: " + invocation.getMethodName() + ", provider: " + getUrl() + ", cause: "
					+ e.getMessage(), e);
		}
	}

	@Override
	public boolean isAvailable() {
		if (!super.isAvailable())
			return false;
		for (ExchangeClient client : clients) {
			if (client.isConnected() && !client.hasAttribute(Constants.CHANNEL_ATTRIBUTE_READONLY_KEY)) {
				// cannot write == not Available ?
				return true;
			}
		}
		return false;
	}

	public void destroy() {
		// 防止client被关闭多次.在connect per jvm的情况下，client.close方法会调用计数器-1，当计数器小于等于0的情况下，才真正关闭
		if (super.isDestroyed()) {
			return;
		} else {
			// dubbo check ,避免多次关闭
			destroyLock.lock();
			try {
				if (super.isDestroyed()) {
					return;
				}
				super.destroy();
				if (invokers != null) {
					invokers.remove(this);
				}
				for (ExchangeClient client : clients) {
					try {
						client.close();
					} catch (Throwable t) {
						logger.warn(t.getMessage(), t);
					}
				}

			} finally {
				destroyLock.unlock();
			}
		}
	}
}