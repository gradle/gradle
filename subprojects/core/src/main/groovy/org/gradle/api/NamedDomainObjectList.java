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

import java.util.List;

/**
 * <p>A specialisation of {@link org.gradle.api.NamedDomainObjectCollection} that also implements {@link java.util.List}.</p>
 *
 * <p>All object equality is determined in terms of object names. That is, calling {@code remove()} with an object that is NOT equal to
 * an existing object in terms of {@code equals}, but IS in terms of name equality will result in the existing collection item with
 * the equal name being removed.</p>
 *
 * @param <T> The type of element in the set
 */
public interface NamedDomainObjectList<T> extends NamedDomainObjectCollection<T>, List<T> {
    /**
     * {@inheritDoc}
     */
    <S extends T> NamedDomainObjectList<S> withType(Class<S> type);

    /**
     * {@inheritDoc}
     */
    NamedDomainObjectList<T> matching(Spec<? super T> spec);

    /**
     * {@inheritDoc}
     */
    NamedDomainObjectList<T> matching(Closure spec);

    /**
     * {@inheritDoc}
     */
    List<T> findAll(Closure spec);
}
