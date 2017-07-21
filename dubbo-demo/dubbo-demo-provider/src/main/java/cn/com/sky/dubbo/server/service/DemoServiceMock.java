package cn.com.sky.dubbo.server.service;

import cn.com.sky.dubbo.server.User;

public class DemoServiceMock implements DemoService {

	public String sayHello(String name) {
		return "provider: i am mock ! Hello '" + name + "'";
	}

	@Override
	public void addUser(User u) {
		throw new RuntimeException("add user fail!");
	}

	@Override
	public User getUserById(Integer id) {
		return null;
	}

}