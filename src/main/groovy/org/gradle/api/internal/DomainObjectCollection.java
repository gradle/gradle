/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal;

import org.gradle.api.specs.Spec;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.Action;

import java.util.Set;
import java.util.Map;

import groovy.lang.Closure;

public interface DomainObjectCollection<T> extends Iterable<T> {
    Set<T> getAll();

    Map<String, T> getAsMap();

    T findByName(String name);

    T getByName(String name) throws UnknownDomainObjectException;

    T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException;

    T getAt(String name) throws UnknownDomainObjectException;

    Set<T> findAll(Spec<? super T> spec);

    <S extends T> DomainObjectCollection<S> withType(Class<S> type);

    DomainObjectCollection<T> matching(Spec<? super T> spec);

    Action<? super T> whenObjectAdded(Action<? super T> action);

    void whenObjectAdded(Closure action);

    Action<? super T> whenObjectRemoved(Action<? super T> action);

    void allObjects(Action<? super T> action);

    void allObjects(Closure action);
}
