/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.PathValidation;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.WorkResult;

import java.io.File;
import java.net.URI;
import java.util.Map;

public interface FileOperations {
    File file(Object path);

    File file(Object path, PathValidation validation);

    URI uri(Object path);

    FileResolver getFileResolver();

    String relativePath(Object path);

    ConfigurableFileCollection files(Object... paths);

    ConfigurableFileCollection files(Object paths, Closure configureClosure);

    ConfigurableFileTree fileTree(Object baseDir);

    ConfigurableFileTree fileTree(Map<String, ?> args);

    @Deprecated
    ConfigurableFileTree fileTree(Closure closure);

    ConfigurableFileTree fileTree(Object baseDir, Closure closure);

    FileTree zipTree(Object zipPath);

    FileTree tarTree(Object tarPath);

    CopySpec copySpec(Closure closure);

    CopySpec copySpec(Action<? super CopySpec> action);

    WorkResult copy(Closure closure);

    WorkResult sync(Action<? super CopySpec> action);

    File mkdir(Object path);

    boolean delete(Object... paths);

    ResourceHandler getResources();
}