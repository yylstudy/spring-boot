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

package org.springframework.boot.autoconfigure.condition;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionMessage.Style;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * {@link Condition} and {@link AutoConfigurationImportFilter} that checks for the
 * presence or absence of specific classes.
 *
 * @author Phillip Webb
 * @see ConditionalOnClass
 * @see ConditionalOnMissingClass
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
class OnClassCondition extends SpringBootCondition
		implements AutoConfigurationImportFilter, BeanFactoryAware, BeanClassLoaderAware {
	/**
	 * DefaultlistableBeanFactory
	 */
	private BeanFactory beanFactory;

	private ClassLoader beanClassLoader;

	/**
	 * 匹配
	 * @param autoConfigurationClasses 候选的自动配置类
	 * @param autoConfigurationMetadata PropertiesAutoConfigurationMetadata
	 * @return
	 */
	@Override
	public boolean[] match(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		//获取ConditionEvaluationReport
		ConditionEvaluationReport report = getConditionEvaluationReport();
		//获取条件结果
		ConditionOutcome[] outcomes = getOutcomes(autoConfigurationClasses,
				autoConfigurationMetadata);
		boolean[] match = new boolean[outcomes.length];
		for (int i = 0; i < outcomes.length; i++) {
			match[i] = (outcomes[i] == null || outcomes[i].isMatch());
			if (!match[i] && outcomes[i] != null) {
				logOutcome(autoConfigurationClasses[i], outcomes[i]);
				if (report != null) {
					report.recordConditionEvaluation(autoConfigurationClasses[i], this,
							outcomes[i]);
				}
			}
		}
		return match;
	}

	private ConditionEvaluationReport getConditionEvaluationReport() {
		if (this.beanFactory != null
				&& this.beanFactory instanceof ConfigurableBeanFactory) {
			return ConditionEvaluationReport
					.get((ConfigurableListableBeanFactory) this.beanFactory);
		}
		return null;
	}

	/**
	 * 获取结果
	 * @param autoConfigurationClasses
	 * @param autoConfigurationMetadata
	 * @return
	 */
	private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
			AutoConfigurationMetadata autoConfigurationMetadata) {
		// Split the work and perform half in a background thread. Using a single
		// additional thread seems to offer the best performance. More threads make
		// things worse
		int split = autoConfigurationClasses.length / 2;
		OutcomesResolver firstHalfResolver = createOutcomesResolver(
				autoConfigurationClasses, 0, split, autoConfigurationMetadata);
		OutcomesResolver secondHalfResolver = new StandardOutcomesResolver(
				autoConfigurationClasses, split, autoConfigurationClasses.length,
				autoConfigurationMetadata, this.beanClassLoader);
		ConditionOutcome[] secondHalf = secondHalfResolver.resolveOutcomes();
		ConditionOutcome[] firstHalf = firstHalfResolver.resolveOutcomes();
		ConditionOutcome[] outcomes = new ConditionOutcome[autoConfigurationClasses.length];
		System.arraycopy(firstHalf, 0, outcomes, 0, firstHalf.length);
		System.arraycopy(secondHalf, 0, outcomes, split, secondHalf.length);
		return outcomes;
	}

	/**
	 * 创建结果解析器
	 * @param autoConfigurationClasses 候选的自动配置类
	 * @param start 开始下标
	 * @param end 结束下标
	 * @param autoConfigurationMetadata
	 * @return
	 */
	private OutcomesResolver createOutcomesResolver(String[] autoConfigurationClasses,
			int start, int end, AutoConfigurationMetadata autoConfigurationMetadata) {
		//创建标准的输出解析器
		OutcomesResolver outcomesResolver = new StandardOutcomesResolver(
				autoConfigurationClasses, start, end, autoConfigurationMetadata,
				this.beanClassLoader);
		try {
			//创建线程输出解析器
			return new ThreadedOutcomesResolver(outcomesResolver);
		}
		catch (AccessControlException ex) {
			return outcomesResolver;
		}
	}

	/**
	 * 获取匹配的条件结果
	 * @param context 条件上下文 ConditionContextImpl
	 * @param metadata @Configuration 所在类的元数据
	 * @return
	 */
	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		ClassLoader classLoader = context.getClassLoader();
		ConditionMessage matchMessage = ConditionMessage.empty();
		//获取自动配置类的@ConditionalOnClass注解的value、name方法的值
		List<String> onClasses = getCandidates(metadata, ConditionalOnClass.class);
		if (onClasses != null) {
			List<String> missing = getMatches(onClasses, MatchType.MISSING, classLoader);
			if (!missing.isEmpty()) {
				return ConditionOutcome
						.noMatch(ConditionMessage.forCondition(ConditionalOnClass.class)
								.didNotFind("required class", "required classes")
								.items(Style.QUOTE, missing));
			}
			matchMessage = matchMessage.andCondition(ConditionalOnClass.class)
					.found("required class", "required classes").items(Style.QUOTE,
							//获取匹配者
							getMatches(onClasses, MatchType.PRESENT, classLoader));
		}
		List<String> onMissingClasses = getCandidates(metadata,
				ConditionalOnMissingClass.class);
		if (onMissingClasses != null) {
			List<String> present = getMatches(onMissingClasses, MatchType.PRESENT,
					classLoader);
			if (!present.isEmpty()) {
				return ConditionOutcome.noMatch(
						ConditionMessage.forCondition(ConditionalOnMissingClass.class)
								.found("unwanted class", "unwanted classes")
								.items(Style.QUOTE, present));
			}
			matchMessage = matchMessage.andCondition(ConditionalOnMissingClass.class)
					.didNotFind("unwanted class", "unwanted classes").items(Style.QUOTE,
							getMatches(onMissingClasses, MatchType.MISSING, classLoader));
		}
		return ConditionOutcome.match(matchMessage);
	}

	/**
	 * 获取自动配置类的对应注解的value、name方法的值
	 * @param metadata
	 * @param annotationType
	 * @return
	 */
	private List<String> getCandidates(AnnotatedTypeMetadata metadata,
			Class<?> annotationType) {
		MultiValueMap<String, Object> attributes = metadata
				.getAllAnnotationAttributes(annotationType.getName(), true);
		if (attributes == null) {
			return Collections.emptyList();
		}
		List<String> candidates = new ArrayList<>();
		addAll(candidates, attributes.get("value"));
		addAll(candidates, attributes.get("name"));
		return candidates;
	}

	private void addAll(List<String> list, List<Object> itemsToAdd) {
		if (itemsToAdd != null) {
			for (Object item : itemsToAdd) {
				Collections.addAll(list, (String[]) item);
			}
		}
	}

	/**
	 * 获取匹配
	 * @param candidates
	 * @param matchType
	 * @param classLoader
	 * @return
	 */
	private List<String> getMatches(Collection<String> candidates, MatchType matchType,
			ClassLoader classLoader) {
		List<String> matches = new ArrayList<>(candidates.size());
		for (String candidate : candidates) {
			if (matchType.matches(candidate, classLoader)) {
				matches.add(candidate);
			}
		}
		return matches;
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.beanClassLoader = classLoader;
	}

	private enum MatchType {

		PRESENT {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return isPresent(className, classLoader);
			}

		},

		MISSING {

			@Override
			public boolean matches(String className, ClassLoader classLoader) {
				return !isPresent(className, classLoader);
			}

		};

		private static boolean isPresent(String className, ClassLoader classLoader) {
			if (classLoader == null) {
				classLoader = ClassUtils.getDefaultClassLoader();
			}
			try {
				forName(className, classLoader);
				return true;
			}
			catch (Throwable ex) {
				return false;
			}
		}

		private static Class<?> forName(String className, ClassLoader classLoader)
				throws ClassNotFoundException {
			if (classLoader != null) {
				return classLoader.loadClass(className);
			}
			return Class.forName(className);
		}

		public abstract boolean matches(String className, ClassLoader classLoader);

	}

	private interface OutcomesResolver {

		ConditionOutcome[] resolveOutcomes();

	}

	private static final class ThreadedOutcomesResolver implements OutcomesResolver {
		/**
		 * 线程类
		 */
		private final Thread thread;
		/**
		 * 输出结果条件
		 */
		private volatile ConditionOutcome[] outcomes;

		/**
		 * 创建一个线程输出解析器
		 * @param outcomesResolver StandardOutcomesResolver
		 */
		private ThreadedOutcomesResolver(OutcomesResolver outcomesResolver) {
			this.thread = new Thread(
					//解析输出结果
					() -> this.outcomes = outcomesResolver.resolveOutcomes());
			this.thread.start();
		}

		@Override
		public ConditionOutcome[] resolveOutcomes() {
			try {
				this.thread.join();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			return this.outcomes;
		}

	}

	private final class StandardOutcomesResolver implements OutcomesResolver {
		/**
		 * 候选的自动配置类
		 */
		private final String[] autoConfigurationClasses;
		/**
		 * 开始下标
		 */
		private final int start;
		/**
		 * 结束下标
		 */
		private final int end;
		/**
		 * 自动配置元数据
		 */
		private final AutoConfigurationMetadata autoConfigurationMetadata;

		private final ClassLoader beanClassLoader;

		private StandardOutcomesResolver(String[] autoConfigurationClasses, int start,
				int end, AutoConfigurationMetadata autoConfigurationMetadata,
				ClassLoader beanClassLoader) {
			this.autoConfigurationClasses = autoConfigurationClasses;
			this.start = start;
			this.end = end;
			this.autoConfigurationMetadata = autoConfigurationMetadata;
			this.beanClassLoader = beanClassLoader;
		}

		/**
		 * 获取输出结果条件
		 * @return
		 */
		@Override
		public ConditionOutcome[] resolveOutcomes() {
			return getOutcomes(this.autoConfigurationClasses, this.start, this.end,
					this.autoConfigurationMetadata);
		}

		private ConditionOutcome[] getOutcomes(String[] autoConfigurationClasses,
				int start, int end, AutoConfigurationMetadata autoConfigurationMetadata) {
			ConditionOutcome[] outcomes = new ConditionOutcome[end - start];
			for (int i = start; i < end; i++) {
				//候选的自动配置类
				String autoConfigurationClass = autoConfigurationClasses[i];
				//查找对应自动配置的ConditionalOnClass
				//例如AOP 是org.springframework.context.annotation.EnableAspectJAutoProxy,
				//org.aspectj.lang.annotation.Aspect,
				//org.aspectj.lang.reflect.Advice,
				//org.aspectj.weaver.AnnotatedElement
				Set<String> candidates = autoConfigurationMetadata
						.getSet(autoConfigurationClass, "ConditionalOnClass");
				//配置文件中存在
				if (candidates != null) {
					//
					outcomes[i - start] = getOutcome(candidates);
				}
			}
			return outcomes;
		}

		/**
		 * 获取输出结果
		 * @param candidates
		 * @return
		 */
		private ConditionOutcome getOutcome(Set<String> candidates) {
			try {
				//获取匹配的
				List<String> missing = getMatches(candidates, MatchType.MISSING,
						this.beanClassLoader);
				if (!missing.isEmpty()) {
					return ConditionOutcome.noMatch(
							ConditionMessage.forCondition(ConditionalOnClass.class)
									.didNotFind("required class", "required classes")
									.items(Style.QUOTE, missing));
				}
			}
			catch (Exception ex) {
				// We'll get another chance later
			}
			return null;
		}

	}

}
