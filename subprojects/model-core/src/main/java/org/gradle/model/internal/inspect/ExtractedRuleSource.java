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

package org.gradle.model.internal.inspect;

import org.gradle.internal.Factory;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.registry.ModelRegistry;

import java.util.List;

public interface ExtractedRuleSource<T> {
    /**
     * Applies the rules of this rule source to the given element.
     */
    void apply(ModelRegistry modelRegistry, MutableModelNode target);

    /**
     * Returns the set of plugins required by the rules of this rule source.
     */
    List<? extends Class<?>> getRequiredPlugins();

    /**
     * Asserts that no additional plugins are required by the rules of this rule source.
     */
    void assertNoPlugins() throws UnsupportedOperationException;

    /**
     * Creates a factory for creating views of this rule source, for invoking rules or binding references. Should be used only before the rule source has been applied.
     */
    Factory<? extends T> getFactory();
}
