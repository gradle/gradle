/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.language.base.internal.model;

import org.gradle.internal.Cast;

public class DefaultVariantDimensionSelectorFactory implements VariantDimensionSelectorFactory {
    private final Class<?> predicate;
    private final VariantDimensionSelector<?> selector;

    public static <T> DefaultVariantDimensionSelectorFactory of(Class<T> clazz, VariantDimensionSelector<T> selector) {
        return new DefaultVariantDimensionSelectorFactory(clazz, selector);
    }

    private DefaultVariantDimensionSelectorFactory(Class<?> clazz, VariantDimensionSelector<?> selector) {
        this.predicate = clazz;
        this.selector = selector;
    }


    @Override
    public <T> VariantDimensionSelector<T> getVariantDimensionSelector(T o) {
        if (o!=null && predicate.isAssignableFrom(o.getClass())) {
            return Cast.uncheckedCast(selector);
        }
        return null;
    }
}
