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

import groovy.lang.Closure;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.Rule;
import org.gradle.api.specs.Spec;

import java.util.Set;
import java.util.Map;
import java.util.List;

public interface DomainObjectContainer<T> {
    Set<T> getAll();

    Set<T> get(Spec<? super T> spec);

    Map<String, T> getAsMap();

    T find(String name);

    T get(String name) throws UnknownDomainObjectException;

    T get(String name, Closure configureClosure) throws UnknownDomainObjectException;

    Rule addRule(Rule rule);

    List<Rule> getRules();
}
