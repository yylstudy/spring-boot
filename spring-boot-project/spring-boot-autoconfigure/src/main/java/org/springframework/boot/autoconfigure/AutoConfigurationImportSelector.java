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

package org.springframework.boot.autoconfigure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * {@link DeferredImportSelector} to handle {@link EnableAutoConfiguration
 * auto-configuration}. This class can also be subclassed if a custom variant of
 * {@link EnableAutoConfiguration @EnableAutoConfiguration} is needed.
 *
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Madhura Bhave
 * @since 1.3.0
 * @see EnableAutoConfiguration
 */

/**
 * 自动配置ImportSelector 实现了BeanClassLoaderAware、ResourceLoaderAware、BeanFactoryAware、
 * EnvironmentAware，所以会执行想用的Aware方法
 */
public class AutoConfigurationImportSelector
		implements DeferredImportSelector, BeanClassLoaderAware, ResourceLoaderAware,
		BeanFactoryAware, EnvironmentAware, Ordered {

	private static final String[] NO_IMPORTS = {};

	private static final Log logger = LogFactory
			.getLog(AutoConfigurationImportSelector.class);

	private static final String PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE = "spring.autoconfigure.exclude";
	/**
	 * DefaultlistableBeanFactory bean工厂
	 */
	private ConfigurableListableBeanFactory beanFactory;
	/**
	 * 环境对象
	 */
	private Environment environment;
	/**
	 * 类加载器
	 */
	private ClassLoader beanClassLoader;
	/**
	 * 资源加载器
	 */
	private ResourceLoader resourceLoader;

	/**
	 * 获取自动配置的所有Import
	 * @param annotationMetadata
	 * @return
	 */
	@Override
	public String[] selectImports(AnnotationMetadata annotationMetadata) {
		//是否开启自动注解
		if (!isEnabled(annotationMetadata)) {
			return NO_IMPORTS;
		}
		//spring boot 源码中不存在spring-autoconfigure-metadata.properties文件，但是其对应jar包又有，搞不懂
		//返回类型是PropertiesAutoConfigurationMetadata
		AutoConfigurationMetadata autoConfigurationMetadata = AutoConfigurationMetadataLoader
				.loadMetadata(this.beanClassLoader);
		//获取EnableAutoConfiguration的注解属性
		AnnotationAttributes attributes = getAttributes(annotationMetadata);
		//获取匹配的配置项
		List<String> configurations = getCandidateConfigurations(annotationMetadata,
				attributes);
		//删除重复的
		configurations = removeDuplicates(configurations);
		//获取排除的
		Set<String> exclusions = getExclusions(annotationMetadata, attributes);
		//检查排除的类
		checkExcludedClasses(configurations, exclusions);
		//匹配中删除排除的类
		configurations.removeAll(exclusions);
		//过滤
		//最终剩下的
		//org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration
		//org.springframework.boot.autoconfigure.aop.AopAutoConfiguration  AOP的自动配置
		//org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
		//org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
		//org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
		//org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
		//org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
		//org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration
		//org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
		//org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration
		//org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration
		//org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
		//org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration
		//org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration
		//org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration
		//org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
		//org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
		//org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration
		//org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
		//org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration
		//org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration
		//org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
		//过滤
		configurations = filter(configurations, autoConfigurationMetadata);
		//执行自动配置Import事件
		fireAutoConfigurationImportEvents(configurations, exclusions);
		return StringUtils.toStringArray(configurations);
	}

	@Override
	public Class<? extends Group> getImportGroup() {
		return AutoConfigurationGroup.class;
	}

	protected boolean isEnabled(AnnotationMetadata metadata) {
		if (getClass() == AutoConfigurationImportSelector.class) {
			return getEnvironment().getProperty(
					EnableAutoConfiguration.ENABLED_OVERRIDE_PROPERTY, Boolean.class,
					true);
		}
		return true;
	}

	/**
	 * Return the appropriate {@link AnnotationAttributes} from the
	 * {@link AnnotationMetadata}. By default this method will return attributes for
	 * {@link #getAnnotationClass()}.
	 * @param metadata the annotation metadata
	 * @return annotation attributes
	 */
	/**
	 * 获取EnableAutoConfiguration的属性注解
	 * @param metadata
	 * @return
	 */
	protected AnnotationAttributes getAttributes(AnnotationMetadata metadata) {
		String name = getAnnotationClass().getName();
		AnnotationAttributes attributes = AnnotationAttributes
				.fromMap(metadata.getAnnotationAttributes(name, true));
		Assert.notNull(attributes,
				() -> "No auto-configuration attributes found. Is "
						+ metadata.getClassName() + " annotated with "
						+ ClassUtils.getShortName(name) + "?");
		return attributes;
	}

	/**
	 * Return the source annotation class used by the selector.
	 * @return the annotation class
	 */
	protected Class<?> getAnnotationClass() {
		return EnableAutoConfiguration.class;
	}

	/**
	 * Return the auto-configuration class names that should be considered. By default
	 * this method will load candidates using {@link SpringFactoriesLoader} with
	 * {@link #getSpringFactoriesLoaderFactoryClass()}.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return a list of candidate configurations
	 */
	/**
	 * 获取匹配的配置项
	 * @param metadata
	 * @param attributes
	 * @return
	 */
	protected List<String> getCandidateConfigurations(AnnotationMetadata metadata,
			AnnotationAttributes attributes) {
		//加载
		List<String> configurations = SpringFactoriesLoader.loadFactoryNames(
				getSpringFactoriesLoaderFactoryClass(), getBeanClassLoader());
		Assert.notEmpty(configurations,
				"No auto configuration classes found in META-INF/spring.factories. If you "
						+ "are using a custom packaging, make sure that file is correct.");
		return configurations;
	}

	/**
	 * Return the class used by {@link SpringFactoriesLoader} to load configuration
	 * candidates.
	 * @return the factory class
	 */
	protected Class<?> getSpringFactoriesLoaderFactoryClass() {
		return EnableAutoConfiguration.class;
	}

	private void checkExcludedClasses(List<String> configurations,
			Set<String> exclusions) {
		List<String> invalidExcludes = new ArrayList<>(exclusions.size());
		for (String exclusion : exclusions) {
			if (ClassUtils.isPresent(exclusion, getClass().getClassLoader())
					&& !configurations.contains(exclusion)) {
				invalidExcludes.add(exclusion);
			}
		}
		if (!invalidExcludes.isEmpty()) {
			handleInvalidExcludes(invalidExcludes);
		}
	}

	/**
	 * Handle any invalid excludes that have been specified.
	 * @param invalidExcludes the list of invalid excludes (will always have at least one
	 * element)
	 */
	protected void handleInvalidExcludes(List<String> invalidExcludes) {
		StringBuilder message = new StringBuilder();
		for (String exclude : invalidExcludes) {
			message.append("\t- ").append(exclude).append(String.format("%n"));
		}
		throw new IllegalStateException(String
				.format("The following classes could not be excluded because they are"
						+ " not auto-configuration classes:%n%s", message));
	}

	/**
	 * Return any exclusions that limit the candidate configurations.
	 * @param metadata the source metadata
	 * @param attributes the {@link #getAttributes(AnnotationMetadata) annotation
	 * attributes}
	 * @return exclusions or an empty set
	 */
	protected Set<String> getExclusions(AnnotationMetadata metadata,
			AnnotationAttributes attributes) {
		Set<String> excluded = new LinkedHashSet<>();
		excluded.addAll(asList(attributes, "exclude"));
		excluded.addAll(Arrays.asList(attributes.getStringArray("excludeName")));
		excluded.addAll(getExcludeAutoConfigurationsProperty());
		return excluded;
	}

	private List<String> getExcludeAutoConfigurationsProperty() {
		if (getEnvironment() instanceof ConfigurableEnvironment) {
			Binder binder = Binder.get(getEnvironment());
			return binder.bind(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class)
					.map(Arrays::asList).orElse(Collections.emptyList());
		}
		String[] excludes = getEnvironment()
				.getProperty(PROPERTY_NAME_AUTOCONFIGURE_EXCLUDE, String[].class);
		return (excludes != null) ? Arrays.asList(excludes) : Collections.emptyList();
	}

	/**
	 *
	 * @param configurations
	 * @param autoConfigurationMetadata
	 * @return
	 */
	private List<String> filter(List<String> configurations,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		long startTime = System.nanoTime();
		String[] candidates = StringUtils.toStringArray(configurations);
		boolean[] skip = new boolean[candidates.length];
		boolean skipped = false;
		//遍历 org.springframework.boot.autoconfigure.condition.OnClassCondition
		for (AutoConfigurationImportFilter filter : getAutoConfigurationImportFilters()) {
			invokeAwareMethods(filter);
			boolean[] match = filter.match(candidates, autoConfigurationMetadata);
			for (int i = 0; i < match.length; i++) {
				if (!match[i]) {
					skip[i] = true;
					skipped = true;
				}
			}
		}
		if (!skipped) {
			return configurations;
		}
		List<String> result = new ArrayList<>(candidates.length);
		for (int i = 0; i < candidates.length; i++) {
			if (!skip[i]) {
				result.add(candidates[i]);
			}
		}
		if (logger.isTraceEnabled()) {
			int numberFiltered = configurations.size() - result.size();
			logger.trace("Filtered " + numberFiltered + " auto configuration class in "
					+ TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime)
					+ " ms");
		}
		return new ArrayList<>(result);
	}

	protected List<AutoConfigurationImportFilter> getAutoConfigurationImportFilters() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportFilter.class,
				this.beanClassLoader);
	}

	protected final <T> List<T> removeDuplicates(List<T> list) {
		return new ArrayList<>(new LinkedHashSet<>(list));
	}

	protected final List<String> asList(AnnotationAttributes attributes, String name) {
		String[] value = attributes.getStringArray(name);
		return Arrays.asList((value != null) ? value : new String[0]);
	}

	/**
	 * 执行自动配置Import事件
	 * @param configurations
	 * @param exclusions
	 */
	private void fireAutoConfigurationImportEvents(List<String> configurations,
			Set<String> exclusions) {
		//获取自动配置Import监听
		//org.springframework.boot.autoconfigure.condition.ConditionEvaluationReportAutoConfigurationImportListener
		List<AutoConfigurationImportListener> listeners = getAutoConfigurationImportListeners();
		if (!listeners.isEmpty()) {
			//创建一个自动配置Import事件，事件源是AutoConfigurationImportSelector
			AutoConfigurationImportEvent event = new AutoConfigurationImportEvent(this,
					configurations, exclusions);
			for (AutoConfigurationImportListener listener : listeners) {
				//执行Aware方法
				invokeAwareMethods(listener);
				//监听自动配置Import事件
				listener.onAutoConfigurationImportEvent(event);
			}
		}
	}

	protected List<AutoConfigurationImportListener> getAutoConfigurationImportListeners() {
		return SpringFactoriesLoader.loadFactories(AutoConfigurationImportListener.class,
				this.beanClassLoader);
	}

	private void invokeAwareMethods(Object instance) {
		if (instance instanceof Aware) {
			if (instance instanceof BeanClassLoaderAware) {
				((BeanClassLoaderAware) instance)
						.setBeanClassLoader(this.beanClassLoader);
			}
			if (instance instanceof BeanFactoryAware) {
				((BeanFactoryAware) instance).setBeanFactory(this.beanFactory);
			}
			if (instance instanceof EnvironmentAware) {
				((EnvironmentAware) instance).setEnvironment(this.environment);
			}
			if (instance instanceof ResourceLoaderAware) {
				((ResourceLoaderAware) instance).setResourceLoader(this.resourceLoader);
			}
		}
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory);
		this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
	}

	protected final ConfigurableListableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}

	/**
	 * 设置类加载器
	 * @param classLoader
	 */
	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	protected ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	/**
	 * 设置环境对象
	 * @param environment
	 */
	@Override
	public void setEnvironment(Environment environment) {
		this.environment = environment;
	}

	protected final Environment getEnvironment() {
		return this.environment;
	}

	/**
	 * 资源加载器
	 * @param resourceLoader
	 */
	@Override
	public void setResourceLoader(ResourceLoader resourceLoader) {
		this.resourceLoader = resourceLoader;
	}

	protected final ResourceLoader getResourceLoader() {
		return this.resourceLoader;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 1;
	}

	private static class AutoConfigurationGroup implements DeferredImportSelector.Group,
			BeanClassLoaderAware, BeanFactoryAware, ResourceLoaderAware {
		/**
		 * 类加载器
		 */
		private ClassLoader beanClassLoader;
		/**
		 * DefaultlistableBeanFactory
		 */
		private BeanFactory beanFactory;
		/**
		 * 资源加载器
		 */
		private ResourceLoader resourceLoader;
		/**
		 * 匹配的自动注入的Import和SpringBootApplication注解类的Metadata的映射
		 //org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration
		 //@ConfigurationProperties注解的自动配置
		 //org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration
		 //org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
		 //org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
		 //org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration
		 //org.springframework.boot.autoconfigure.http.codec.CodecsAutoConfiguration
		 //org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration
		 //org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
		 //org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration
		 //org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration
		 //org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
		 //org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration
		 //org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration
		 */
		private final Map<String, AnnotationMetadata> entries = new LinkedHashMap<>();

		@Override
		public void setBeanClassLoader(ClassLoader classLoader) {
			this.beanClassLoader = classLoader;
		}

		@Override
		public void setBeanFactory(BeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		public void setResourceLoader(ResourceLoader resourceLoader) {
			this.resourceLoader = resourceLoader;
		}

		/**
		 * 处理方法
		 * @param annotationMetadata
		 * @param deferredImportSelector
		 */
		@Override
		public void process(AnnotationMetadata annotationMetadata,
				DeferredImportSelector deferredImportSelector) {
			String[] imports = deferredImportSelector.selectImports(annotationMetadata);
			//添加自动配置的映射缓存
			for (String importClassName : imports) {
				this.entries.put(importClassName, annotationMetadata);
			}
		}

		/**
		 * 获取自动注入的映射缓存
		 * @return
		 */
		@Override
		public Iterable<Entry> selectImports() {
			return sortAutoConfigurations().stream()
					.map((importClassName) -> new Entry(this.entries.get(importClassName),
							importClassName))
					.collect(Collectors.toList());
		}

		private List<String> sortAutoConfigurations() {
			List<String> autoConfigurations = new ArrayList<>(this.entries.keySet());
			if (this.entries.size() <= 1) {
				return autoConfigurations;
			}
			AutoConfigurationMetadata autoConfigurationMetadata = AutoConfigurationMetadataLoader
					.loadMetadata(this.beanClassLoader);
			return new AutoConfigurationSorter(getMetadataReaderFactory(),
					autoConfigurationMetadata).getInPriorityOrder(autoConfigurations);
		}

		private MetadataReaderFactory getMetadataReaderFactory() {
			try {
				return this.beanFactory.getBean(
						SharedMetadataReaderFactoryContextInitializer.BEAN_NAME,
						MetadataReaderFactory.class);
			}
			catch (NoSuchBeanDefinitionException ex) {
				return new CachingMetadataReaderFactory(this.resourceLoader);
			}
		}

	}

}
