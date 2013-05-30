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
package org.gradle.nativecode.base;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.Named;

import java.util.List;

/**
 * Represents a logical software component, which may be built in a number of variant binaries.
 */
@Incubating
public interface NativeComponent extends Named {

    /**
     * The source sets that are used to build this component.
     */
    DomainObjectSet<SourceSet> getSourceSets();

    /**
     * The binaries that are built for this component.
     */
    DomainObjectSet<NativeBinary> getBinaries();

    /**
     * The name that is used to construct task names and output file names when building this component.
     */
    String getBaseName();

    /**
     * Sets the name that is used to construct task names and output file names when building this component.
     */
    void setBaseName(String baseName);

    /**
     * The arguments passed when compiling this component.
     */
    List<Object> getCompilerArgs();

    /**
     * Adds a number of arguments to be passed to the compiler.
     */
    void compilerArgs(Object... args);

    /**
     * The arguments passed when linking this component.
     */
    List<Object> getLinkerArgs();

    /**
     * Adds a number of arguments to be passed to the linker.
     */
    void linkerArgs(Object... args);
}
