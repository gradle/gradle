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
package org.gradle.api;

import groovy.lang.Closure;

import java.util.List;

/**
 * A {@code DomainObjectContainer} represents a mutable collection of domain objects of type {@code T}.
 *
 * @param <T> The type of domain objects in this container.
 */
public interface NamedDomainObjectContainer<T> extends NamedDomainObjectCollection<T> {

    /**
     * Adds a rule to this container. The given rule is invoked when an unknown object is requested by name.
     *
     * @param rule The rule to add.
     * @return The added rule.
     */
    Rule addRule(Rule rule);

    /**
     * Adds a rule to this container. The given closure is executed when an unknown object is requested by name. The
     * requested name is passed to the closure as a parameter.
     *
     * @param description The description of the rule.
     * @param ruleAction The closure to execute to apply the rule.
     * @return The added rule.
     */
    Rule addRule(String description, Closure ruleAction);

    /**
     * Returns the rules used by this container.
     *
     * @return The rules, in the order they will be applied.
     */
    List<Rule> getRules();
}
