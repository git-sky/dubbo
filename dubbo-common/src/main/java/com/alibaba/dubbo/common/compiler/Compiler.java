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
package com.alibaba.dubbo.common.compiler;

import com.alibaba.dubbo.common.extension.SPI;

/**
 * 
 * <pre>
 * Compiler. (SPI, Singleton, ThreadSafe)
 * 
 * @author william.liangf
 * 
 * 
 *       
 * 
 * SPI注解表示如果没有配置，dubbo默认选用javassist编译源代码。     (javassist=com.alibaba.dubbo.common.compiler.support.JavassistCompiler)
 * 
 * 接口方法compile第一个入参code，就是java的源代码。
 * 
 * 接口方法compile第二个入参classLoader，按理是类加载器用来加载编译后的字节码，其实没用到，都是根据当前线程或者调用方的classLoader加载的。
 * 
 * </pre>
 */
@SPI("javassist")
public interface Compiler {

	/**
	 * Compile java source code.
	 * 
	 * @param code
	 *            Java source code
	 * @param classLoader
	 *            TODO
	 * @return Compiled class
	 */
	Class<?> compile(String code, ClassLoader classLoader);

}
