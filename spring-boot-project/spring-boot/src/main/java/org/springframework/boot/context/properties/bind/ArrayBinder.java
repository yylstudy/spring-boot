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

package org.springframework.boot.context.properties.bind;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.boot.context.properties.bind.Binder.Context;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.ResolvableType;

/**
 * {@link AggregateBinder} for arrays.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
class ArrayBinder extends IndexedElementsBinder<Object> {

	ArrayBinder(Context context) {
		super(context);
	}

	/**
	 * 获取绑定集合结果
	 * @param name 配置文件名称
	 * @param target the target to bind
	 * @param elementBinder an element binder
	 * @return
	 */
	@Override
	protected Object bindAggregate(ConfigurationPropertyName name, Bindable<?> target,
			AggregateElementBinder elementBinder) {
		//获取
		IndexedCollectionSupplier result = new IndexedCollectionSupplier(ArrayList::new);
		//获取解析类型 java.lang.String[]
		ResolvableType aggregateType = target.getType();
		//获取通用类型  java.lang.String
		ResolvableType elementType = target.getType().getComponentType();
		//绑定下标
		bindIndexed(name, target, elementBinder, aggregateType, elementType, result);
		if (result.wasSupplied()) {
			List<Object> list = (List<Object>) result.get();
			Object array = Array.newInstance(elementType.resolve(), list.size());
			for (int i = 0; i < list.size(); i++) {
				Array.set(array, i, list.get(i));
			}
			return array;
		}
		return null;
	}

	@Override
	protected Object merge(Supplier<?> existing, Object additional) {
		return additional;
	}

}
