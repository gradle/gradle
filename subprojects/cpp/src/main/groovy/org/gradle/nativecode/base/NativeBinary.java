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
import org.gradle.api.Nullable;
import org.gradle.language.base.Binary;

import java.io.File;
import java.util.List;

/**
 * Represents a particular binary artifact that is the result of building a native component.
 */
@Incubating
public interface NativeBinary extends Binary, Buildable {

    // TODO:DAZ Remove this?
    String getOutputFileName();

    File getOutputFile();

    void setOutputFile(File outputFile);

    DomainObjectSet<SourceSet> getSourceSets();

    NativeComponent getComponent();

    List<Object> getCompilerArgs();

    List<Object> getLinkerArgs();

    String getTaskName(@Nullable String verb);

    DomainObjectSet<LibraryBinary> getLibs();

    void builtBy(Object... tasks);
}
