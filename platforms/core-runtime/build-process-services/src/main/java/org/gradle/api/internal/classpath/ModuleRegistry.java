/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A registry of dynamically loadable modules from a Gradle distribution.
 */
@ServiceScope(Scope.Global.class)
public interface ModuleRegistry {

    /**
     * Locates a module by name, if it exists.
     *
     * @return the optional module, or null if it cannot be found.
     */
    @Nullable
    Module findModule(String name);

    /**
     * Locates a module by name.
     *
     * @return the module.
     *
     * @throws UnknownModuleException if the requested module cannot be found.
     */
    Module getModule(String name) throws UnknownModuleException;

    /**
     * Given an initial set of root modules, walk all dependencies of all modules
     * until the transitive closure of all required modules is discovered.
     *
     * @param roots The root modules to begin the traversal from.
     *
     * @return The transitive closure of required modules, including the root modules.
     */
    default List<Module> getRuntimeModules(Iterable<Module> roots) {
        Set<String> seen = new HashSet<>();
        Deque<Module> queue = new ArrayDeque<>();

        for (Module root : roots) {
            if (seen.add(root.getName())) {
                queue.add(root);
            }
        }

        // We implement BFS to attempt to keep more frequently used classes closer
        // to the beginning of the classpath, as we expect classes to be loaded from
        // them more often, thus potentially improving classloading performance.
        List<Module> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Module module = queue.remove();
            result.add(module);

            for (String name : module.getDependencyNames()) {
                if (seen.add(name)) {
                    Module dependency = getModule(name);
                    queue.add(dependency);
                }
            }
        }

        return result;
    }

    /**
     * Return the transitive classpath required to execute the given root modules,
     * including the implementation of the given root modules.
     *
     * @param roots The modules to get the classpath of.
     *
     * @return The classpath required to execute the given root modules.
     */
    default ClassPath getRuntimeClasspath(Iterable<Module> roots) {
        ClassPath cp = ClassPath.EMPTY;
        for (Module module : getRuntimeModules(roots)) {
            cp = cp.plus(module.getImplementationClasspath());
        }
        return cp;
    }

    /**
     * Get the entire runtime classpath for the module with the given name, including
     * the module's implementation and the implementation of the transitive closure of
     * all modules that the given module depends on.
     *
     * @param moduleName The name of the module to get the runtime classpath for.
     *
     * @return The runtime classpath for the module.
     */
    default ClassPath getRuntimeClasspath(String moduleName) {
        return getRuntimeClasspath(Collections.singleton(getModule(moduleName)));
    }

}
