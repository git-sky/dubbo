package cn.com.sky.dubbo.client;

import cn.com.sky.dubbo.server.service.DemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Consumer1 {
	private static final Logger logger = LoggerFactory.getLogger(Consumer1.class);

	private static String getPath() {
		String path = Consumer1.class.getPackage().getName();
		String p = path.replaceAll("\\.", "/");
		System.out.println(p);
		return p;
	}

	public static void main(String[] args) {

		String configLocation = getPath() + "/consumer.xml";

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
		for (int i = 0; i < 100; i++) {
			String result = demoService.sayHello("world"+i); // 执行远程方法
			System.out.println("end:" + (System.currentTimeMillis() - begin));
			System.out.println(result); // 显示调用结果
		}

		// HelloAction helloAction = (HelloAction) context.getBean("helloAction"); // 获取远程服务代理
		// long begin2 = System.currentTimeMillis();
		// System.out.println("begin:" + begin2);
		// String result2 = helloAction.say("world"); // 执行远程方法
		// System.out.println("end:" + (System.currentTimeMillis() - begin2));
		// System.out.println(result2); // 显示调用结果

	}

}
