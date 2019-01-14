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

import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;

import javax.annotation.Nullable;

public class FileTreeValue implements ValidatingValue {
    private final ValidatingValue delegate;
    private final FileTreeInternal fileTree;

    public static FileTreeValue create(FileResolver fileResolver, ValidatingValue value) {
        FileTreeInternal fileTree = fileResolver.resolveFilesAsTree(value);
        return new FileTreeValue(value, fileTree);
    }

    public FileTreeValue(ValidatingValue delegate, FileTreeInternal fileTree) {
        this.delegate = delegate;
        this.fileTree = fileTree;
    }

    @Nullable
    @Override
    public Object call() {
        return fileTree;
    }

    @Override
    public void attachProducer(Task producer) {
        delegate.attachProducer(producer);
    }

    @Override
    public void maybeFinalizeValue() {
        delegate.maybeFinalizeValue();
    }
}
