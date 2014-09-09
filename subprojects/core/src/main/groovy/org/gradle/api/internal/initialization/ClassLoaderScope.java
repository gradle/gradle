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
 * Certain domain objects (e.g. Gradle, Settings, Project) have an associated class loader scope. This is used for evaluating associated scripts and script plugins.
 *
 * Use of this class allows class loader creation to be lazy, and potentially optimised. It also provides a central location for class loader reuse.
 */
public interface ClassLoaderScope {

    /**
     * The classloader for use at this node.
     * <p>
     * Contains exported classes of the parent scope and all local and exported additions to this scope.
     * It is strongly preferable to only call this after {@link #lock() locking} the scope as it allows the structure to be optimized.
     */
    ClassLoader getLocalClassLoader();

    /**
     * The classloader for use by child nodes.
     * <p>
     * Contains exported classes of the parent scope and all local and exported additions to this scope.
     * It is strongly preferable to only call this after {@link #lock() locking} the scope as it allows the structure to be optimized.
     */
    ClassLoader getExportClassLoader();

    /**
     * The parent of this scope.
     */
    ClassLoaderScope getParent();

    /**
     * Makes the provided classes visible to this scope, but not to children. The classes are loaded in their own ClassLoader whose parent is the export
     * ClassLoader of the parent scope.
     *
     * <p>Can not be called after being locked.
     */
    ClassLoaderScope local(ClassPath classPath);

    /**
     * Makes the provided classes visible to this scope and its children. The classes are loaded in their own ClassLoader whose parent is the export ClassLoader
     * of the parent scope.
     *
     * <p>Can not be called after being locked.
     */
    ClassLoaderScope export(ClassPath classPath);

    /**
     * Creates a scope with this scope as parent.
     */
    ClassLoaderScope createChild();

    /**
     * Signal that no more modifications are to come, allowing the structure to be optimised if possible.
     */
    ClassLoaderScope lock();

    boolean isLocked();

}
