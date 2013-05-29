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

package org.gradle.nativecode.base;

import org.gradle.api.Buildable;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.internal.HasInternalProtocol;
import org.gradle.language.base.Binary;

import java.io.File;
import java.util.List;

// TODO:DAZ These don't apply to all binary subtypes: look at splitting this up in to a number of smaller facets / functional interfaces
/**
 * Represents a particular binary artifact that is the result of building a native component.
 */
@Incubating @HasInternalProtocol
public interface NativeBinary extends Binary, Buildable {
    /**
     * The file where this binary will be created.
     */
    File getOutputFile();

    /**
     * Sets the file where this binary will be created.
     */
    void setOutputFile(File outputFile);

    /**
     * The source sets used to create this binary.
     */
    DomainObjectSet<SourceSet> getSourceSets();

    /**
     * The libraries that should be linked into this binary.
     */
    DomainObjectSet<NativeDependencySet> getLibs();

    /**
     * Adds a library as input to this binary. This method accepts the following types:
     *
     * <ul>
     *     <li>A {@link Library}</li>
     *     <li>A {@link LibraryBinary}</li>
     *     <li>A {@link NativeDependencySet}</li>
     * </ul>
     */
    void lib(Object library);

    /**
     * The arguments passed when compiling this binary.
     */
    List<Object> getCompilerArgs();

    /**
     * The arguments passed when linking this binary.
     */
    List<Object> getLinkerArgs();
}
