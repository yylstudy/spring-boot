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

package org.springframework.boot.context.properties.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.ObjectUtils;

/**
 * {@link ConfigurationPropertySource} backed by an {@link EnumerablePropertySource}.
 * Extends {@link SpringConfigurationPropertySource} with full "relaxed" mapping support.
 * In order to use this adapter the underlying {@link PropertySource} must be fully
 * enumerable. A security restricted {@link SystemEnvironmentPropertySource} cannot be
 * adapted.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @see PropertyMapper
 */
class SpringIterableConfigurationPropertySource extends SpringConfigurationPropertySource
		implements IterableConfigurationPropertySource {
	/**
	 * 缓存CacheKey
	 */
	private volatile Object cacheKey;
	/**
	 * 缓存对象
	 */
	private volatile Cache cache;

	/**
	 * 创建 SpringIterableConfigurationPropertySource
	 * @param propertySource OriginTrackedMapPropertySource
	 * @param mapper DelegatingPropertyMapper
	 */
	SpringIterableConfigurationPropertySource(EnumerablePropertySource<?> propertySource,
			PropertyMapper mapper) {
		super(propertySource, mapper, null);
		assertEnumerablePropertySource();
	}

	private void assertEnumerablePropertySource() {
		if (getPropertySource() instanceof MapPropertySource) {
			try {
				((MapPropertySource) getPropertySource()).getSource().size();
			}
			catch (UnsupportedOperationException ex) {
				throw new IllegalArgumentException(
						"PropertySource must be fully enumerable");
			}
		}
	}

	/**
	 * 获取配置属性对象
	 * @param name the name of the property (must not be {@code null})
	 * @return
	 */
	@Override
	public ConfigurationProperty getConfigurationProperty(
			ConfigurationPropertyName name) {
		ConfigurationProperty configurationProperty = super.getConfigurationProperty(
				name);
		if (configurationProperty == null) {
			configurationProperty = find(getPropertyMappings(getCache()), name);
		}
		return configurationProperty;
	}

	@Override
	public Stream<ConfigurationPropertyName> stream() {
		return getConfigurationPropertyNames().stream();
	}

	@Override
	public Iterator<ConfigurationPropertyName> iterator() {
		return getConfigurationPropertyNames().iterator();
	}

	@Override
	public ConfigurationPropertyState containsDescendantOf(
			ConfigurationPropertyName name) {
		return ConfigurationPropertyState.search(this, name::isAncestorOf);
	}

	private List<ConfigurationPropertyName> getConfigurationPropertyNames() {
		Cache cache = getCache();
		List<ConfigurationPropertyName> names = (cache != null) ? cache.getNames() : null;
		if (names != null) {
			return names;
		}
		PropertyMapping[] mappings = getPropertyMappings(cache);
		names = new ArrayList<>(mappings.length);
		for (PropertyMapping mapping : mappings) {
			names.add(mapping.getConfigurationPropertyName());
		}
		names = Collections.unmodifiableList(names);
		if (cache != null) {
			cache.setNames(names);
		}
		return names;
	}

	/**
	 * 获取PropertyMapping对象
	 * @param cache
	 * @return
	 */
	private PropertyMapping[] getPropertyMappings(Cache cache) {
		PropertyMapping[] result = (cache != null) ? cache.getMappings() : null;
		if (result != null) {
			return result;
		}
		//获取配置文件的key名称数组
		String[] names = getPropertySource().getPropertyNames();
		List<PropertyMapping> mappings = new ArrayList<>(names.length * 2);
		for (String name : names) {
			for (PropertyMapping mapping : getMapper().map(name)) {
				mappings.add(mapping);
			}
		}
		result = mappings.toArray(new PropertyMapping[0]);
		if (cache != null) {
			//设置缓存的
			cache.setMappings(result);
		}
		return result;
	}

	/**
	 * 获取缓存对象
	 * @return
	 */
	private Cache getCache() {
		CacheKey cacheKey = CacheKey.get(getPropertySource());
		if (cacheKey == null) {
			return null;
		}
		if (ObjectUtils.nullSafeEquals(cacheKey, this.cacheKey)) {
			return this.cache;
		}
		this.cache = new Cache();
		this.cacheKey = cacheKey.copy();
		return this.cache;
	}

	@Override
	protected EnumerablePropertySource<?> getPropertySource() {
		return (EnumerablePropertySource<?>) super.getPropertySource();
	}

	private static class Cache {

		private List<ConfigurationPropertyName> names;
		/**
		 * 属性映射关系
		 */
		private PropertyMapping[] mappings;

		public List<ConfigurationPropertyName> getNames() {
			return this.names;
		}

		public void setNames(List<ConfigurationPropertyName> names) {
			this.names = names;
		}

		public PropertyMapping[] getMappings() {
			return this.mappings;
		}

		public void setMappings(PropertyMapping[] mappings) {
			this.mappings = mappings;
		}

	}

	private static final class CacheKey {
		/**
		 * 配置文件key集合
		 */
		private final Object key;

		private CacheKey(Object key) {
			this.key = key;
		}

		public CacheKey copy() {
			return new CacheKey(copyKey(this.key));
		}

		private Object copyKey(Object key) {
			if (key instanceof Set) {
				return new HashSet<Object>((Set<?>) key);
			}
			return ((String[]) key).clone();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null || getClass() != obj.getClass()) {
				return false;
			}
			return ObjectUtils.nullSafeEquals(this.key, ((CacheKey) obj).key);
		}

		@Override
		public int hashCode() {
			return this.key.hashCode();
		}

		/**
		 * 获取缓存key对象，OriginTrackedMapPropertySource
		 * @param source
		 * @return
		 */
		public static CacheKey get(EnumerablePropertySource<?> source) {
			if (source instanceof MapPropertySource) {
				return new CacheKey(((MapPropertySource) source).getSource().keySet());
			}
			return new CacheKey(source.getPropertyNames());
		}

	}

}
