/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.classpath.ClassPath;

/**
 * Represents a particular node in the ClassLoader graph.
 *
 * Certain domain objects (e.g. Gradle, Settings, Project) have an associated class loader scope.
 * This is used for evaluating associated scripts and script plugins.
 *
 * Use of this class allows class loader creation to be lazy, and potentially optimised.
 * It also provides a central location for class loader reuse.
 */
public interface ClassLoaderScope {

    /**
     * The effective class loader for this scope.
     *
     * It is strongly preferable to only call this after {@link #lock()}ing the scope as it allows the structure to be optimized.
     */
    ClassLoader getScopeClassLoader();

    /**
     * The class loader that children inherit.
     */
    ClassLoader getChildClassLoader();

    /**
     * The base scope defines the parent for local additions.
     */
    ClassLoaderScope getBase();

    /**
     * Adds a class path visible to this scope, but not to children. The class path is loaded such that the exported classes of the base scope are visible to it.
     * In this way, local classes are effectively the private classes of the scope.
     *
     * <p>Can not be called after being locked.
     */
    ClassLoader addLocal(ClassPath classpath);

    /**
     * Adds a class path visible to this scope and all children. The class path is loaded such that the exported classes of the parent scope are visible to it.
     * In this way, exported classes are effectively the public classes of the scope.
     *
     * <p>Can not be called after being locked.
     */
    ClassLoader export(ClassPath classpath);

    /**
     * Creates a scope with the same parent and base as this scope.
     */
    ClassLoaderScope createSibling();

    /**
     * Creates a scope with this scope as parent. Uses the same base as this scope.
     *
     * Local classes added to the return child will NOT inherit from the exported classes of this and parents. Exported classes WILL inherit from
     * the exported classes of this scope and its parent.
     */
    ClassLoaderScope createChild();

    /**
     * Creates a scope with this scope as parent and base.
     *
     * Both local classes and exported classes added to the return child WILL inherit from the exported classpath of this and parents.
     */
    ClassLoaderScope createRebasedChild();

    /**
     * Signal that no more modifications are to come, allowing the structure to be optimised if possible.
     */
    ClassLoaderScope lock();

    boolean isLocked();

}
