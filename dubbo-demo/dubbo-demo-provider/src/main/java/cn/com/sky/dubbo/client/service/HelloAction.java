package cn.com.sky.dubbo.client.service;

import cn.com.sky.dubbo.server.service.DemoService;

public class HelloAction {

	private DemoService demoService;

	public DemoService getDemoService() {
		return demoService;
	}

	public void setDemoService(DemoService demoService) {
		this.demoService = demoService;
	}

	public String say(String name) {

		String prefix = " hello ";

		String result = demoService.sayHello(name);

		String suffix = " over ";

		return prefix + result + suffix;
	}

}
