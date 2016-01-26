/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.jvm.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.jvm.test.internal.JvmTestSuiteBinaryRules;
import org.gradle.jvm.test.internal.JvmTestSuiteBinarySpecInternal;
import org.gradle.jvm.test.internal.JvmTestSuiteRules;
import org.gradle.model.internal.registry.ModelRegistry;

import javax.inject.Inject;

import static org.gradle.model.internal.core.ModelNodes.withType;
import static org.gradle.model.internal.core.NodePredicate.allDescendants;

/**
 * The base plugin that needs to be applied by all plugins which provide testing support
 * for the Java software model.
 *
 * @since 2.12
 */
@Incubating
public class JvmTestSuiteBasePlugin implements Plugin<Project> {
    private final ModelRegistry modelRegistry;

    @Inject
    public JvmTestSuiteBasePlugin(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JvmTestSuiteRules.class);
        modelRegistry.getRoot().applyTo(allDescendants(withType(JvmTestSuiteBinarySpecInternal.class)), JvmTestSuiteBinaryRules.class);
    }
}
