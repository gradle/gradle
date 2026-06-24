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

package org.gradle.demos.groovy;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;

/**
 * The build-side outputs of one named Groovy source set, mirroring {@link org.gradle.demos.java.JavaClasses}.
 * A fully-managed {@link Named} type so it works as the element of a {@code NamedDomainObjectContainer}
 * auto-created on {@link GroovyLibraryModel#getClasses()}.
 *
 * <p>{@code inputSources} is a {@link ConfigurableFileCollection} (not a {@code SourceDirectorySet}) so
 * the type stays fully managed for container auto-creation.
 */
public interface GroovyClasses extends Named {

    ConfigurableFileCollection getInputSources();

    DirectoryProperty getByteCodeDir();

    DirectoryProperty getProcessedResourcesDir();
}
