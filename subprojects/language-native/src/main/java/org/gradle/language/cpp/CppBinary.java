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

import org.gradle.api.Incubating;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

/**
 * A binary built from C++ source and linked from the resulting object files.
 *
 * @since 4.2
 */
@Incubating
public interface CppBinary extends SoftwareComponent {
    /**
     * Returns the base name of the binary.
     */
    Provider<String> getBaseName();

    /**
     * Returns true if this binary has debugging enabled.
     */
    boolean isDebuggable();

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
}
