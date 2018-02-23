/*
 * Copyright 2007-2008 the original author or authors.
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

/**
 * <p>A {@code Rule} represents some action to perform when an unknown domain object is referenced. The rule can use the
 * domain object name to add an implicit domain object.</p>
 */
public interface Rule {
    /**
     * Returns the description of the rule. This is used for reporting purposes.
     *
     * @return the description. should not return null.
     */
    String getDescription();

    /**
     * Applies this rule for the given unknown domain object. The rule can choose to ignore this name, or add a domain
     * object with the given name.
     *
     * @param domainObjectName The name of the unknown domain object.
     */
    void apply(String domainObjectName);
}
