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

package org.gradle.api.tasks.util;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.PathValidation;
import org.gradle.util.GUtil;
import org.gradle.util.GFileUtils;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class BaseDirConverter {
    public File baseDir(Object path, File baseDir) {
        return baseDir(path, baseDir, PathValidation.NONE);
    }

    public File baseDir(Object path, File baseDir, PathValidation validation) {
        if (!GUtil.isTrue(path) || !GUtil.isTrue(baseDir)) {
            throw new IllegalArgumentException(String.format(
                    "Neither path nor baseDir must be null. path=%s basedir=%s", path, baseDir));
        }
        File file = new File(path.toString());
        if (!file.isAbsolute()) {
            file = new File(baseDir, path.toString());
        }
        file = GFileUtils.canonicalise(file);
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
        return file;
    }
}
