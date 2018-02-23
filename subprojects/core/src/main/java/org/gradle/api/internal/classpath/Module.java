/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.classpath;

import org.gradle.internal.classpath.ClassPath;

import java.util.Set;

/**
 * Meta-data about a dynamically loadable module.
 */
public interface Module {
    /**
     * Returns the classpath for the module implementation. This is the classpath of the module itself. Does not include any dependencies.
     */
    ClassPath getImplementationClasspath();

    /**
     * Returns the classpath containing the runtime dependencies of the module. Does not include any other modules.
     */
    ClassPath getRuntimeClasspath();

    /**
     * Returns implementation + runtime.
     */
    ClassPath getClasspath();

    /**
     * Returns the modules required by this module.
     */
    Set<Module> getRequiredModules();

    /**
     * Returns the transitive closure of all modules required by this module, including the module itself.
     */
    Set<Module> getAllRequiredModules();
}
