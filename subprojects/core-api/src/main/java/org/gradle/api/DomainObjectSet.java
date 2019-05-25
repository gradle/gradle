/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api;

import groovy.lang.Closure;
import org.gradle.api.specs.Spec;

import java.util.Set;

/**
 * <p>A {@code DomainObjectSet} is a specialisation of {@link DomainObjectCollection} that guarantees {@link Set} semantics.</p>
 *
 * @param <T> The type of domain objects in this set.
 */
public interface DomainObjectSet<T> extends DomainObjectCollection<T>, Set<T> {

    /**
     * {@inheritDoc}
     */
    @Override
    <S extends T> DomainObjectSet<S> withType(Class<S> type);

    /**
     * {@inheritDoc}
     */
    @Override
    DomainObjectSet<T> matching(Spec<? super T> spec);

    /**
     * {@inheritDoc}
     */
    @Override
    DomainObjectSet<T> matching(Closure spec);

    /**
     * {@inheritDoc}
     */
    @Override
    Set<T> findAll(Closure spec);
}
