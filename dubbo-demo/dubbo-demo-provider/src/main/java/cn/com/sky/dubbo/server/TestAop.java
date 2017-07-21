package cn.com.sky.dubbo.server;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import cn.com.sky.dubbo.server.service.DemoService;



/**
 * 基于Schema的AOP
 */
public class TestAop {

	private static String getPath() {
		String path = TestAop.class.getPackage().getName();
		String p = path.replaceAll("\\.", "/");
		System.out.println(p);
		return p;
	}

	public static void main(String[] args) {

		String configLocation = getPath() + "/applicationContext2.xml";

		final ClassPathXmlApplicationContext appCtx = new ClassPathXmlApplicationContext(configLocation);
		DemoService userDao = (DemoService) appCtx.getBean("demoService");
		// userDao.add();
		// userDao.delete();
		// userDao.update();
		// userDao.query();
		userDao.sayHello("AAAAAAAAAA");
	}

}