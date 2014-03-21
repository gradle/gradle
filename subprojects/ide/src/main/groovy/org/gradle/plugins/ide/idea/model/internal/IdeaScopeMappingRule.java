/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.idea.model.internal;

import com.google.common.collect.Sets;

import java.util.Set;

/**
 * A description how to assign IDEA classpath scopes to Gradle dependencies.
 *
 * Rules are applied in their order.
 * If a dependency is found in listed configuration(s) it is assigned specified scope or scopes in IDEA project.
 */
class IdeaScopeMappingRule {

    final Set<String> configurationNames;

    IdeaScopeMappingRule(String ... configurationNames) {
        this.configurationNames = Sets.newHashSet(configurationNames);
    }

    @Override
    public String toString() {
        return "IdeaScopeMappingRule{"
                + "configurationNames=" + configurationNames
                + '}';
    }
}
