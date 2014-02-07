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

package org.gradle.nativebinaries;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.nativebinaries.toolchain.ToolChain;

import java.util.Collection;

/**
 * Represents a particular binary artifact that is the result of building a native component.
 */
@Incubating @HasInternalProtocol
public interface ProjectNativeBinary extends NativeBinary {
    /**
     * The component that this binary was built from.
     */
    ProjectNativeComponent getComponent();

    /**
     * Can this binary be built in the current environment?
     */
    boolean isBuildable();

    /**
     * The source sets used to compile this binary.
     */
    DomainObjectSet<LanguageSourceSet> getSource();

    /**
     * Adds one or more {@link org.gradle.language.base.LanguageSourceSet}s that are used to compile this binary.
     * <p/>
     * This method accepts the following types:
     *
     * <ul>
     *     <li>A {@link org.gradle.language.base.FunctionalSourceSet}</li>
     *     <li>A {@link org.gradle.language.base.LanguageSourceSet}</li>
     *     <li>A Collection of {@link org.gradle.language.base.LanguageSourceSet}s</li>
     * </ul>
     */
    void source(Object source);

    /**
     * The libraries that should be linked into this binary.
     */
    Collection<NativeDependencySet> getLibs();

    /**
     * Adds a library as input to this binary.
     * <p/>
     * This method accepts the following types:
     *
     * <ul>
     *     <li>A {@link Library}</li>
     *     <li>A {@link NativeDependencySet}</li>
     *     <li>A {@link java.util.Map} containing the library selector.</li>
     * </ul>
     *
     * The Map notation supports the following String attributes:
     *
     * <ul>
     *     <li>project: the path to the project containing the library (optional, defaults to current project)</li>
     *     <li>library: the name of the library (required)</li>
     *     <li>linkage: the library linkage required ['shared'/'static'] (optional, defaults to 'shared')</li>
     * </ul>
     */
    void lib(Object library);

    /**
     * Returns the {@link org.gradle.nativebinaries.toolchain.ToolChain} that will be used to build this binary.
     */
    ToolChain getToolChain();

    // TODO:DAZ Add these tools as extensions: linker for ExecutableBinary and SharedLibraryBinary, staticLibArchiver for StaticLibraryBinary
    /**
     * The settings used for linking this binary.
     */
    Tool getLinker();

    /**
     * The static archiver settings used for creating this binary.
     */
    Tool getStaticLibArchiver();

    /**
     * The set of tasks associated with this binary.
     */
    NativeBinaryTasks getTasks();
}
