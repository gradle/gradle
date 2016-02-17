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
import org.gradle.api.file.FileTree;
import org.gradle.api.resources.internal.ReadableResourceInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.typeconversion.NotationParser;

import java.io.File;
import java.net.URI;
import java.util.List;

public interface FileResolver extends RelativeFilePathResolver, PathToFileResolver {
    ReadableResourceInternal resolveResource(Object path);

    File resolve(Object path, PathValidation validation);

    FileCollectionInternal resolveFiles(Object... paths);

    FileTreeInternal resolveFilesAsTree(Object... paths);

    FileTreeInternal compositeFileTree(List<? extends FileTree> fileTrees);

    URI resolveUri(Object path);

    NotationParser<Object, File> asNotationParser();

    Factory<PatternSet> getPatternSetFactory();
}
