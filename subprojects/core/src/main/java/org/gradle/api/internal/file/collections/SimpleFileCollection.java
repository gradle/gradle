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
package org.gradle.api.internal.file.collections;

import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;

/**
 * @deprecated Use {@link org.gradle.api.file.ProjectLayout#files(Object...)} or {@link org.gradle.api.file.ProjectLayout#configurableFiles(Object...)}
 */
@Deprecated
public class SimpleFileCollection extends FileCollectionAdapter implements Serializable {
    public SimpleFileCollection(File... files) {
        this(new ListBackedFileSet(files));
    }

    public SimpleFileCollection(Collection<File> files) {
        this(new ListBackedFileSet(files));
    }

    private SimpleFileCollection(MinimalFileSet fileSet) {
        super(fileSet);
        DeprecationLogger.nagUserOfDiscontinuedApi("SimpleFileCollection type", "Please use Project.files() instead.");
    }
}
