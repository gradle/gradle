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

package org.gradle.plugin.use.resolve.internal;

import org.gradle.api.artifacts.Dependency;

/**
 * Collects dependencies required to apply plugins.
 */
public interface PluginResolveContext {

    /**
     * Provide a dependency that will be resolved as part of the script classpath.
     */
    void visitDependency(Dependency dependency);

    /**
     * Provide a classloader that will be included as part of the script classpath.
     */
    void visitClassLoader(ClassLoader classLoader);
}
