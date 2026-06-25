/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.java;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;

/**
 * The build-side outputs of one named source set, mirroring the original {@code JavaClasses}
 * build-model element. A fully-managed {@link Named} type so it works as the element of a
 * {@code NamedDomainObjectContainer} auto-created on {@link JavaLibraryModel#getClasses()}.
 *
 * <p>Divergence from the original: {@code inputSources} is a {@link ConfigurableFileCollection}
 * rather than a {@code SourceDirectorySet}, so the type stays fully managed (a SourceDirectorySet
 * needs custom {@code ObjectFactory} construction and would block container auto-creation).
 */
public interface JavaClasses extends Named {

    ConfigurableFileCollection getInputSources();

    /** The raw compiler output for this source set (where the {@code compile<Name>Java} task writes). */
    DirectoryProperty getClassesDir();

    /**
     * The canonical bytecode downstream consumers (the {@code jar} and {@code test} tasks) read for this
     * source set. By convention it is {@link #getClassesDir()} — the raw compiler output — but a
     * post-processing reaction (e.g. instrumentation) may override it to point at its own output.
     */
    DirectoryProperty getByteCodeDir();

    DirectoryProperty getProcessedResourcesDir();
}
