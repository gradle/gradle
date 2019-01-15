/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.internal.Cast;
import org.gradle.internal.file.PathToFileResolver;

public class FileCollectionHelper {
    public static FileCollectionInternal asFileCollection(PathToFileResolver resolver, Object... paths) {
        if (paths.length == 1 && paths[0] instanceof FileCollection) {
            return Cast.cast(FileCollectionInternal.class, paths[0]);
        }
        return new DefaultConfigurableFileCollection(resolver, null, paths);
    }

    private static FileTreeInternal asFileTree(PathToFileResolver resolver, Object... paths) {
        return Cast.cast(FileTreeInternal.class, asFileCollection(resolver, paths).getAsFileTree());
    }

    public static Object forInputFileValue(PathToFileResolver resolver, InputFilePropertyType inputFilePropertyType, Object path) {
        return inputFilePropertyType == InputFilePropertyType.DIRECTORY ? asFileTree(resolver, path) : path;
    }
}
