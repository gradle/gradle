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
package org.gradle.api.internal.file.copy;

import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

public class CopyActionExecuter {

    private final Instantiator instantiator;
    private final FileSystem fileSystem;

    public CopyActionExecuter(Instantiator instantiator, FileSystem fileSystem) {
        this.instantiator = instantiator;
        this.fileSystem = fileSystem;
    }

    public WorkResult execute(final CopySpecInternal spec, CopyAction action) {
        final CopyAction effectiveVisitor = new DuplicateHandlingCopyActionDecorator(
                new NormalizingCopyActionDecorator(action, fileSystem)
        );

        CopyActionProcessingStream processingStream = new CopySpecBackedCopyActionProcessingStream(spec, instantiator, fileSystem);
        return effectiveVisitor.execute(processingStream);
    }

}