/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.context.properties;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Utility class to memorize {@code @Bean} definition meta data during initialization of
 * the bean factory.
 *
 * @author Dave Syer
 * @since 1.1.0
 */
public class ConfigurationBeanFactoryMetadata implements BeanFactoryPostProcessor {

	/**
	 * The bean name that this class is registered with.
	 */
	public static final String BEAN_NAME = ConfigurationBeanFactoryMetadata.class
			.getName();
	/**
	 * DefaultlistableBeanFactory
	 */
	private ConfigurableListableBeanFactory beanFactory;
	/**
	 * beanName和其拥有的@Bean注解的映射集合
	 */
	private final Map<String, FactoryMetadata> beansFactoryMetadata = new HashMap<>();

	/**
	 * BeanFactoryPostProcessor 的后置方法
	 * @param beanFactory
	 * @throws BeansException
	 */
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
			throws BeansException {
		this.beanFactory = beanFactory;
		//遍历容器中的所有bean，这个主要是
		for (String name : beanFactory.getBeanDefinitionNames()) {
			BeanDefinition definition = beanFactory.getBeanDefinition(name);
			//工厂方法，也就是@Bean注解的方法
			String method = definition.getFactoryMethodName();
			//工厂方法bean，也就是@Bean注解所在的bean
			String bean = definition.getFactoryBeanName();
			if (method != null && bean != null) {
				this.beansFactoryMetadata.put(name, new FactoryMetadata(bean, method));
			}
		}
	}

	public <A extends Annotation> Map<String, Object> getBeansWithFactoryAnnotation(
			Class<A> type) {
		Map<String, Object> result = new HashMap<>();
		for (String name : this.beansFactoryMetadata.keySet()) {
			if (findFactoryAnnotation(name, type) != null) {
				result.put(name, this.beanFactory.getBean(name));
			}
		}
		return result;
	}

	/**
	 * 获取对应类型的注解
	 * @param beanName
	 * @param type
	 * @param <A>
	 * @return
	 */
	public <A extends Annotation> A findFactoryAnnotation(String beanName,
			Class<A> type) {
		Method method = findFactoryMethod(beanName);
		return (method != null) ? AnnotationUtils.findAnnotation(method, type) : null;
	}

	/**
	 * 从@Bean中查找
	 * @param beanName
	 * @return
	 */
	public Method findFactoryMethod(String beanName) {
		if (!this.beansFactoryMetadata.containsKey(beanName)) {
			return null;
		}
		//当前的beanName是@Bean初始化出来的
		AtomicReference<Method> found = new AtomicReference<>(null);
		FactoryMetadata metadata = this.beansFactoryMetadata.get(beanName);
		Class<?> factoryType = this.beanFactory.getType(metadata.getBean());
		String factoryMethod = metadata.getMethod();
		if (ClassUtils.isCglibProxyClass(factoryType)) {
			factoryType = factoryType.getSuperclass();
		}
		ReflectionUtils.doWithMethods(factoryType, (method) -> {
			if (method.getName().equals(factoryMethod)) {
				found.compareAndSet(null, method);
			}
		});
		return found.get();
	}

	private static class FactoryMetadata {
		/**
		 * @Bean 注解所在的bean
		 */
		private final String bean;
		/**
		 * @Bean 注解所在的方法
		 */
		private final String method;

		FactoryMetadata(String bean, String method) {
			this.bean = bean;
			this.method = method;
		}

		public String getBean() {
			return this.bean;
		}

		public String getMethod() {
			return this.method;
		}

	}

}
