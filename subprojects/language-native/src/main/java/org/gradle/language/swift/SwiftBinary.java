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

package org.gradle.language.swift;

import org.gradle.api.Incubating;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;

/**
 * A binary built from Swift source.
 *
 * @since 4.2
 */
@Incubating
public interface SwiftBinary extends SoftwareComponent {
    /**
     * Returns the name of the Swift module that this binary defines.
     */
    Provider<String> getModule();

    /**
     * Returns true if this binary has debugging enabled.
     */
    boolean isDebuggable();

    /**
     * Returns true if this binary has testing enabled.
     *
     * @since 4.4
     */
    boolean isTestable();

    /**
     * Returns the Swift source files of this binary.
     */
    FileCollection getSwiftSource();

    /**
     * Returns the modules to use to compile this binary. Includes the module file of this binary's dependencies.
     *
     * @since 4.4
     */
    FileCollection getCompileModules();

    /**
     * Returns the link libraries to use to link this binary. Includes the link libraries of the component's dependencies.
     */
    FileCollection getLinkLibraries();

    /**
     * Returns the runtime libraries required by this binary. Includes the runtime libraries of the component's dependencies.
     */
    FileCollection getRuntimeLibraries();

    /**
     * Returns the object files created for this binary.
     *
     * @since 4.4
     */
    FileCollection getObjects();
}
