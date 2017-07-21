package cn.com.sky.dubbo.server.service;

import cn.com.sky.dubbo.server.User;

public interface DemoService {

	String sayHello(String name);

	void addUser(User u);

	User getUserById(Integer id);

}