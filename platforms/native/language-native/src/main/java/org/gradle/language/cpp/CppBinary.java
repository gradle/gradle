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

package org.gradle.language.cpp;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.component.BuildableComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.language.ComponentWithDependencies;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.ComponentWithObjectFiles;
import org.gradle.nativeplatform.Linkage;

/**
 * A binary built from C++ source and linked from the resulting object files.
 *
 * @since 4.2
 */
public interface CppBinary extends ComponentWithObjectFiles, ComponentWithDependencies, BuildableComponent {
    /**
     * The dependency resolution attribute use to indicate whether a binary is debuggable or not.
     */
    Attribute<Boolean> DEBUGGABLE_ATTRIBUTE = Attribute.of("org.gradle.native.debuggable", Boolean.class);

    /**
     * The dependency resolution attribute use to indicate whether a binary is optimized or not.
     *
     * @since 4.5
     */
    Attribute<Boolean> OPTIMIZED_ATTRIBUTE = Attribute.of("org.gradle.native.optimized", Boolean.class);

    /**
     * The dependency resolution attribute use to indicate which linkage a binary uses.
     *
     * @since 4.5
     */
    Attribute<Linkage> LINKAGE_ATTRIBUTE = Attribute.of("org.gradle.native.linkage", Linkage.class);

    /**
     * Returns the C++ source files of this binary.
     */
    FileCollection getCppSource();

    /**
     * Returns the header directories to use to compile this binary. Includes the header directories of this binary plus those of its dependencies.
     */
    FileCollection getCompileIncludePath();

    /**
     * Returns the link libraries to use to link this binary. Includes the link libraries of the component's dependencies.
     */
    FileCollection getLinkLibraries();

    /**
     * Returns the runtime libraries required by this binary. Includes the runtime libraries of the component's dependencies.
     */
    FileCollection getRuntimeLibraries();

    /**
     * Returns the target platform for this component.
     *
     * @since 4.5
     */
    CppPlatform getTargetPlatform();

    /**
     * Returns the compile task for this binary.
     *
     * @since 4.5
     */
    Provider<CppCompile> getCompileTask();
}
