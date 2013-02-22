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
package org.gradle.api.tasks;

import org.gradle.api.*;
import org.gradle.api.tasks.compile.AbstractCompile;

import java.io.File;

/**
 * An exploded binary containing resources and compiled class files.
 */
// TODO: maybe we need to allow additional dirs like SourceSetOutput does
// (esp. for backwards compatibility). Wonder if it's still necessary to distinguish
// between classes and resources dirs, instead of just maintaining a collection of dirs.
// As far as generated resources are concerned, it might be better to model
// them as an additional (Buildable) ResourceSet.
@Incubating
public interface ClassDirectoryBinary extends Named, Buildable {
    File getClassesDir();
    void setClassesDir(File dir);
    File getResourcesDir();
    void setResourcesDir(File dir);
    DomainObjectCollection<LanguageSourceSet> getSource();
    Task getClassesTask();
    void setClassesTask(Task task);
    @Nullable
    Copy getResourcesTask();
    void setResourcesTask(Copy task);
    // TODO: having a single compile task won't work if multiple separately compiled langs are used
    @Nullable
    AbstractCompile getCompileTask();
    void setCompileTask(AbstractCompile task);
    String getTaskName(String verb, String target);
    String getTaskBaseName();
}
