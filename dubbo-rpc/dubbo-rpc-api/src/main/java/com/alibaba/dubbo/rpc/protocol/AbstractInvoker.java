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

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.Version;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.NetUtils;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Result;
import com.alibaba.dubbo.rpc.RpcInvocation;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.support.RpcUtils;

/**
 * AbstractInvoker.
 * 
 * @author qian.lei
 * @author william.liangf
 */
public abstract class AbstractInvoker<T> implements Invoker<T> {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	private final Class<T> type;

	private final URL url;

	private final Map<String, String> attachment;

	private volatile boolean available = true;

	private volatile boolean destroyed = false;

	public AbstractInvoker(Class<T> type, URL url) {
		this(type, url, (Map<String, String>) null);
	}

	public AbstractInvoker(Class<T> type, URL url, String[] keys) {
		this(type, url, convertAttachment(url, keys));
	}

	public AbstractInvoker(Class<T> type, URL url, Map<String, String> attachment) {
		if (type == null)
			throw new IllegalArgumentException("service type == null");
		if (url == null)
			throw new IllegalArgumentException("service url == null");
		this.type = type;
		this.url = url;
		this.attachment = attachment == null ? null : Collections.unmodifiableMap(attachment);
	}

	private static Map<String, String> convertAttachment(URL url, String[] keys) {
		if (keys == null || keys.length == 0) {
			return null;
		}
		Map<String, String> attachment = new HashMap<String, String>();
		for (String key : keys) {
			String value = url.getParameter(key);
			if (value != null && value.length() > 0) {
				attachment.put(key, value);
			}
		}
		return attachment;
	}

	public Class<T> getInterface() {
		return type;
	}

	public URL getUrl() {
		return url;
	}

	public boolean isAvailable() {
		return available;
	}

	protected void setAvailable(boolean available) {
		this.available = available;
	}

	public void destroy() {
		if (isDestroyed()) {
			return;
		}
		destroyed = true;
		setAvailable(false);
	}

	public boolean isDestroyed() {
		return destroyed;
	}

	public String toString() {
		return getInterface() + " -> " + (getUrl() == null ? "" : getUrl().toString());
	}

	/**
	 * 补充Invocation的参数，并且执行调用 doInvoke(invocation);
	 * 
	 * 参见： InvokerInvocationHandler
	 * 
	 * SKY_COMMENT DubboInvoker
	 */
	public Result invoke(Invocation inv) throws RpcException {
		if (destroyed) {
			throw new RpcException("Rpc invoker for service " + this + " on consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion()
					+ " is DESTROYED, can not be invoked any more!");
		}
		// 1、设置invoker
		RpcInvocation invocation = (RpcInvocation) inv;
		invocation.setInvoker(this);

		// 2、设置invocation自定义携带的数据
		 // 填充接口参数
		if (attachment != null && attachment.size() > 0) {
			invocation.addAttachmentsIfAbsent(attachment);
		}
		 // 填充业务系统需要透传的参数
		Map<String, String> context = RpcContext.getContext().getAttachments();
		if (context != null) {
			invocation.addAttachmentsIfAbsent(context);
		}
		  // 默认是同步调用，但也支持异步
		if (getUrl().getMethodParameter(invocation.getMethodName(), Constants.ASYNC_KEY, false)) {
			invocation.setAttachment(Constants.ASYNC_KEY, Boolean.TRUE.toString());
		}

		// 3、异步操作需要添加id，做幂等用。
		//幂等操作:异步操作默认添加invocationid，它是一个自增的AtomicLong
		RpcUtils.attachInvocationIdIfAsync(getUrl(), invocation);

		try {
			// 4、调用,执行具体的RPC操作
			return doInvoke(invocation);
		} catch (InvocationTargetException e) { // biz exception
			Throwable te = e.getTargetException();
			if (te == null) {
				return new RpcResult(e);
			} else {
				if (te instanceof RpcException) {
					((RpcException) te).setCode(RpcException.BIZ_EXCEPTION);
				}
				return new RpcResult(te);
			}
		} catch (RpcException e) {
			if (e.isBiz()) {
				return new RpcResult(e);
			} else {
				throw e;
			}
		} catch (Throwable e) {
			return new RpcResult(e);
		}
	}

	protected abstract Result doInvoke(Invocation invocation) throws Throwable;

}