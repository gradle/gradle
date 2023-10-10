/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.Incubating;
import org.gradle.api.Transformer;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Represents a property whose type is a {@link Set} of elements of type {@link T}. Retains iteration order.
 *
 * <p>
 * You can create a {@link SetProperty} instance using factory method {@link org.gradle.api.model.ObjectFactory#setProperty(Class)}.
 * </p>
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors.
 *
 * @param <T> the type of elements.
 * @since 4.5
 */
public interface SetProperty<T> extends Provider<Set<T>>, HasMultipleValues<T> {
    /**
     * {@inheritDoc}
     */
    @Override
    SetProperty<T> empty();

    /**
     * {@inheritDoc}
     */
    @Override
    SetProperty<T> value(@Nullable Iterable<? extends T> elements);

    /**
     * {@inheritDoc}
     */
    @Override
    SetProperty<T> value(Provider<? extends Iterable<? extends T>> provider);

    /**
     * {@inheritDoc}
     */
    @Override
    SetProperty<T> convention(@Nullable Iterable<? extends T> elements);

    /**
     * {@inheritDoc}
     */
    @Override
    SetProperty<T> convention(Provider<? extends Iterable<? extends T>> provider);

    /**
     * {@inheritDoc}
     */
    @Incubating
    @Override
    default SetProperty<T> update(Transformer<? extends Provider<? extends Iterable<? extends T>>, ? super Provider<? extends Iterable<? extends T>>> transformer) {
        return updateSet(transformer);
    }

    /**
     * Updates the value of this property in place by executing the provided transformer.
     * The transformer accepts the frozen value of this property.
     *
     * @param transformer the transformer to apply to frozen value of the property
     * @return this
     * @since 8.5
     */
    @Incubating
    SetProperty<T> updateSet(Transformer<? extends Provider<? extends Iterable<? extends T>>, ? super Provider<? extends Set<? extends T>>> transformer);
}
