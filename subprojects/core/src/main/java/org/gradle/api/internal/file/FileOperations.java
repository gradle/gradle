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

import org.gradle.api.Action;
import org.gradle.api.PathValidation;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.net.URI;
import java.util.Map;

@ServiceScope(Scopes.Build.class)
public interface FileOperations {
    File file(Object path);

    File file(Object path, PathValidation validation);

    URI uri(Object path);

    FileResolver getFileResolver();

    String relativePath(Object path);

    /**
     * Creates a mutable file collection and initializes it with the given paths.
     */
    ConfigurableFileCollection configurableFiles(Object... paths);

    /**
     * Creates an immutable file collection with the given paths. The paths are resolved
     * with the file resolver.
     *
     * @see #getFileResolver()
     */
    FileCollection immutableFiles(Object... paths);

    ConfigurableFileTree fileTree(Object baseDir);

    ConfigurableFileTree fileTree(Map<String, ?> args);

    FileTree zipTree(Object zipPath);

    FileTree zipTreeNoLocking(Object zipPath);

    FileTree tarTree(Object tarPath);

    CopySpec copySpec();

    WorkResult copy(Action<? super CopySpec> action);

    WorkResult sync(Action<? super SyncSpec> action);

    File mkdir(Object path);

    boolean delete(Object... paths);

    WorkResult delete(Action<? super DeleteSpec> action);

    ResourceHandler getResources();

    PatternSet patternSet();
}
