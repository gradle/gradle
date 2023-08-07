/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.initialization;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Resolves a build script classpath to a set of files in a composite build, ensuring that the
 * required tasks are executed to build artifacts in included builds.
 */
@ServiceScope(Scopes.Build.class)
public interface ScriptClassPathResolver {
    /**
     * Prepares the given configuration for script classpath resolution, setting the relevant attributes and other metadata.
     */
    void prepareClassPath(Configuration configuration, DependencyHandler dependencyHandler);

    ClassPath resolveClassPath(Configuration classpath, DependencyHandler dependencyHandler);
}
