/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;

import java.io.File;

public abstract class AbstractFileResolver implements FileResolver {
    protected File convertObjectToFile(Object path) {
        File file;
        if (path instanceof File) {
            file = (File) path;
        } else {
            file = new File(path.toString());
        }

        return file;
    }

    protected void validate(File file, PathValidation validation) {
        if (validation != PathValidation.NONE) {
            switch (validation) {
                case EXISTS:
                    if (!file.exists()) {
                        throw new InvalidUserDataException(String.format("File '%s' does not exist.", file));
                    }
                    break;
                case FILE:
                    if (!file.exists()) {
                        throw new InvalidUserDataException(String.format("File '%s' does not exist.", file));
                    }
                    if (!file.isFile()) {
                        throw new InvalidUserDataException(String.format("File '%s' is not a file.", file));
                    }
                    break;
                case DIRECTORY:
                    if (!file.exists()) {
                        throw new InvalidUserDataException(String.format("Directory '%s' does not exist.", file));
                    }
                    if (!file.isDirectory()) {
                        throw new InvalidUserDataException(String.format("Directory '%s' is not a directory.", file));
                    }
                    break;
            }
        }
    }

    public FileCollection resolveFiles(Object... paths) {
        if (paths.length == 1 && paths[0] instanceof FileCollection) {
            return (FileCollection) paths[0];
        }
        return new PathResolvingFileCollection(this, paths);
    }

    public FileTree resolveFilesAsTree(Object... paths) {
        return resolveFiles(paths).getAsFileTree();
    }
}
