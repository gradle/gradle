/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.detection;

import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.worker.ForkedTestClasspath;
import org.gradle.internal.classpath.ClassPath;

/**
 * Constructs the implementation classpath for a test process.
 *
 * @see ForkedTestClasspath
 */
public class ForkedTestClasspathFactory {

    private final ModuleRegistry moduleRegistry;

    public ForkedTestClasspathFactory(ModuleRegistry moduleRegistry) {
        this.moduleRegistry = moduleRegistry;
    }

    public ClassPath create() {
        return ClassPath.EMPTY
            .plus(moduleRegistry.getModule("gradle-worker-main").getAllRequiredModulesClasspath())
            .plus(moduleRegistry.getModule("gradle-testing-base-infrastructure").getAllRequiredModulesClasspath())
            .plus(moduleRegistry.getModule("gradle-testing-jvm-infrastructure").getAllRequiredModulesClasspath());
    }

}
