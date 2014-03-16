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

import org.gradle.api.PathValidation;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.resources.ReadableResource;
import org.gradle.internal.Factory;
import org.gradle.internal.typeconversion.NotationParser;

import java.io.File;
import java.net.URI;
import java.util.List;

public interface FileResolver {
    File resolve(Object path);

    ReadableResource resolveResource(Object path);

    File resolve(Object path, PathValidation validation);

    Factory<File> resolveLater(Object path);
    
    FileCollection resolveFiles(Object... paths);

    FileTree resolveFilesAsTree(Object... paths);

    FileTree compositeFileTree(List<FileTree> fileTrees);

    URI resolveUri(Object path);

    String resolveAsRelativePath(Object path);

    NotationParser<Object, File> asNotationParser();
}
