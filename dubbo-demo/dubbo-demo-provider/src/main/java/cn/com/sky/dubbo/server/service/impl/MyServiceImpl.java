package cn.com.sky.dubbo.server.service.impl;

import cn.com.sky.dubbo.server.service.MyService;

public class MyServiceImpl implements MyService {

	@Override
	public String add(String name) {
		return "add";
	}

	@Override
	public void sub() {
		System.out.println("sub");
	}

	@Override
	public String multiply() {
		return "multiply";
	}

}