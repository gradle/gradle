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
import org.gradle.api.internal.HasInternalProtocol;
import org.gradle.language.base.Binary;
import org.gradle.language.base.LanguageSourceSet;

import java.io.File;
import java.util.Collection;

// TODO:DAZ These don't apply to all binary subtypes: look at splitting this up in to a number of smaller facets / functional interfaces
/**
 * Represents a particular binary artifact that is the result of building a native component.
 */
@Incubating @HasInternalProtocol
public interface NativeBinary extends Binary {
    /**
     * The component that this binary was built from.
     */
    NativeComponent getComponent();

    /**
     * The flavor that this binary was built with.
     */
    Flavor getFlavor();

    /**
     * The file where this binary will be created.
     */
    File getOutputFile();

    /**
     * Sets the file where this binary will be created.
     */
    void setOutputFile(File outputFile);

    /**
     * The source sets used to compile this binary.
     */
    DomainObjectSet<LanguageSourceSet> getSource();

    /**
     * Adds one or more {@link LanguageSourceSet}s that are used to compile this binary.
     * <p/>
     * This method accepts the following types:
     *
     * <ul>
     *     <li>A {@link org.gradle.language.base.FunctionalSourceSet}</li>
     *     <li>A {@link LanguageSourceSet}</li>
     *     <li>A Collection of {@link LanguageSourceSet}s</li>
     * </ul>
     */
    void source(Object source);

    /**
     * Returns the {@link ToolChain} that will be used to build this binary.
     */
    ToolChain getToolChain();

    /**
     * Returns the {@link Platform} that this binary is targeted to run on.
     */
    Platform getTargetPlatform();

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
     *     <li>A {@link Library}</li>
     *     <li>A {@link LibraryBinary}</li>
     *     <li>A {@link NativeDependencySet}</li>
     * </ul>
     */
    void lib(Object library);

    /**
     * The settings used for linking this binary.
     */
    Tool getLinker();

    /**
     * The set of tasks associated with this binary.
     */
    NativeBinaryTasks getTasks();

    /**
     * Can this binary be built in the current environment?
     */
    boolean isBuildable();
}
