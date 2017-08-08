package cn.com.sky.dubbo.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Provider {

	private static final Logger logger = LoggerFactory.getLogger(Provider.class);

	private static String getPath() {
		String path = Provider.class.getPackage().getName();
		String p = path.replaceAll("\\.", "/");
		System.out.println(p);
		return p;
	}

	public static void main(String[] args) throws Exception {

		String configLocation = getPath() + "/provider.xml";

		final ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(configLocation);

		context.start();
		logger.info("Shop Service started successfully ");
		System.out.println("Shop Service started successfully ");
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Shutdown hook was invoked. Shutting down Shop Service.");
				context.close();
			}
		});

		System.in.read(); // 按任意键退出
	}

}