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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.toolchain.NativeToolChain;
import org.gradle.platform.base.BinarySpec;

import java.util.Collection;

/**
 * Represents a binary artifact that is the result of building a native component.
 */
@Incubating @HasInternalProtocol
public interface NativeBinarySpec extends BinarySpec {
    /**
     * The component that this binary was built from.
     */
    NativeComponentSpec getComponent();

    /**
     * The flavor that this binary was built with.
     */
    Flavor getFlavor();

    /**
     * Returns the {@link org.gradle.nativeplatform.platform.NativePlatform} that this binary is targeted to run on.
     */
    NativePlatform getTargetPlatform();

    /**
     * Returns the {@link BuildType} used to construct this binary.
     */
    BuildType getBuildType();

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
     *     <li>A {@link NativeLibrarySpec}</li>
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
     * Returns the {@link org.gradle.nativeplatform.toolchain.NativeToolChain} that will be used to build this binary.
     */
    NativeToolChain getToolChain();

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
