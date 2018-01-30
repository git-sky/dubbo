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
package com.alibaba.dubbo.remoting.transport.dispatcher.all;

import java.util.concurrent.ExecutorService;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.remoting.Channel;
import com.alibaba.dubbo.remoting.ChannelHandler;
import com.alibaba.dubbo.remoting.ExecutionException;
import com.alibaba.dubbo.remoting.RemotingException;
import com.alibaba.dubbo.remoting.transport.dispatcher.ChannelEventRunnable;
import com.alibaba.dubbo.remoting.transport.dispatcher.WrappedChannelHandler;
import com.alibaba.dubbo.remoting.transport.dispatcher.ChannelEventRunnable.ChannelState;

/**
 * 该Dubbo的Handler非常重要，因为从这里是IO线程池和服务调用线程池的边界线，该Handler将服务调用操作直接提交给服务调用线程池并返回。
 */
public class AllChannelHandler extends WrappedChannelHandler {

	public AllChannelHandler(ChannelHandler handler, URL url) {
		super(handler, url);
	}

	public void connected(Channel channel) throws RemotingException {
		ExecutorService cexecutor = getExecutorService();
		try {
			cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CONNECTED));
		} catch (Throwable t) {
			throw new ExecutionException("connect event", channel, getClass() + " error when process connected event .", t);
		}
	}

	public void disconnected(Channel channel) throws RemotingException {
		ExecutorService cexecutor = getExecutorService();
		try {
			cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.DISCONNECTED));
		} catch (Throwable t) {
			throw new ExecutionException("disconnect event", channel, getClass() + " error when process disconnected event .", t);
		}
	}

	public void received(Channel channel, Object message) throws RemotingException {
		// 获取服务调用线程池
		ExecutorService cexecutor = getExecutorService();
		// 我们注意到这里对execute进行了异常捕获，这是因为IO线程池是无界的（0-Integer.MAX_VALUE），但服务调用线程池是有界的，所以进行execute提交可能会遇到RejectedExecutionException异常
		//如果你没有指定，服务调用线程池默认的size是200，并且使用的是SynchronousQueue队列，请看FixedThreadPool#getExecutor实现.
		try {
			cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.RECEIVED, message));
		} catch (Throwable t) {
			throw new ExecutionException(message, channel, getClass() + " error when process received event .", t);
		}
	}

	public void caught(Channel channel, Throwable exception) throws RemotingException {
		ExecutorService cexecutor = getExecutorService();
		try {
			cexecutor.execute(new ChannelEventRunnable(channel, handler, ChannelState.CAUGHT, exception));
		} catch (Throwable t) {
			throw new ExecutionException("caught event", channel, getClass() + " error when process caught event .", t);
		}
	}

	private ExecutorService getExecutorService() {
		ExecutorService cexecutor = executor;
		if (cexecutor == null || cexecutor.isShutdown()) {
			cexecutor = SHARED_EXECUTOR;
		}
		return cexecutor;
	}
}