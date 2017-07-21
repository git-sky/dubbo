package cn.com.sky.dubbo.client;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import cn.com.sky.dubbo.client.service.HelloAction;
import cn.com.sky.dubbo.server.service.DemoService;

import com.alibaba.dubbo.rpc.RpcContext;

/**
 * 默认是同步消息
 * 
 * 设置异步消息：async="true"
 */
public class ConsumerAsync {
	private static final Logger logger = LoggerFactory.getLogger(ConsumerAsync.class);

	private static String getPath() {
		String path = ConsumerAsync.class.getPackage().getName();
		String p = path.replaceAll("\\.", "/");
		System.out.println(p);
		return p;
	}

	public static void main(String[] args) throws InterruptedException, ExecutionException {

		String configLocation = getPath() + "/consumer_async.xml";

		final ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(configLocation);

		context.start();
		logger.info("Shop Service started successfully");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Shutdown hook was invoked. Shutting down Shop Service.");
				context.close();
			}
		});

		DemoService demoService = (DemoService) context.getBean("demoService"); // 获取远程服务代理
		long begin = System.currentTimeMillis();
		System.out.println("begin:" + begin);
		String result = demoService.sayHello("world"); // 执行远程方法
		System.out.println("end:" + (System.currentTimeMillis() - begin));
		System.out.println(result); // 显示调用结果

		Future<String> fooFuture = RpcContext.getContext().getFuture(); // 拿到调用的Future引用，当结果返回后，会被通知和设置到此Future。
		String s = fooFuture.get();
		System.out.println(s);

		HelloAction helloAction = (HelloAction) context.getBean("helloAction"); // 获取远程服务代理
		long begin2 = System.currentTimeMillis();
		System.out.println("begin:" + begin2);
		String result2 = helloAction.say("world"); // 执行远程方法
		System.out.println("end:" + (System.currentTimeMillis() - begin2));
		System.out.println(result2); // 显示调用结果

		Future<String> fooFuture2 = RpcContext.getContext().getFuture(); // 拿到调用的Future引用，当结果返回后，会被通知和设置到此Future。
		String s2 = fooFuture2.get();
		System.out.println(s2);

	}

}