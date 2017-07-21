package cn.com.sky.dubbo.server;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 通知
 */
public class MyAdvice {

	public void doBefore(JoinPoint point) throws Throwable {
		System.out.println("doBefore ..." + point.getSignature().getName());
	}

	public void doBefore2(JoinPoint point) throws Throwable {
		System.out.println("doBefore2 ..." + point.getSignature().getName());
	}

	public void doBefore3(JoinPoint point) throws Throwable {
		System.out.println("doBefore3 ..." + point.getSignature().getName());
	}

	public void doBefore4(JoinPoint point) throws Throwable {
		System.out.println("doBefore4 ..." + point.getSignature().getName());
	}

	public Object doAround(ProceedingJoinPoint point) throws Throwable {
		System.out.println("doAround 1 ..." + point.getSignature().getName());
		Object obj = null;
		if (point.getArgs().length > 0) {
			obj = point.proceed(point.getArgs());
		} else {
			obj = point.proceed();
		}
		System.out.println("doAround 2 ..." + point.getSignature().getName());
		return obj;
	}

	public void doAfter(JoinPoint point) throws Throwable {
		System.out.println("doAfter ..." + point.getSignature().getName());
	}

	public void doAfterReturning(JoinPoint point) throws Throwable {
		System.out.println("doAfterReturning ..." + point.getSignature().getName());
	}

	public String doAfterReturning(JoinPoint point, String retValue) throws Throwable {
		System.out.println("doAfterReturning ..." + point.getSignature().getName());
		System.out.println("retValue ..." + retValue);
		return retValue;

	}

	public void doAfterThrowing(JoinPoint point, Throwable ex) {
		System.out.println("doThrowing::method " + point.getTarget().getClass().getName() + "." + point.getSignature().getName() + " throw exception");
		System.out.println(ex.getMessage());
	}

}
