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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

public class CopyActionExecuter {

    private final Instantiator instantiator;
    private final PropertyFactory propertyFactory;
    private final FileSystem fileSystem;
    private final boolean reproducibleFileOrder;
    private final DocumentationRegistry documentationRegistry;

    public CopyActionExecuter(Instantiator instantiator, PropertyFactory propertyFactory, FileSystem fileSystem, boolean reproducibleFileOrder, DocumentationRegistry documentationRegistry) {
        this.instantiator = instantiator;
        this.propertyFactory = propertyFactory;
        this.fileSystem = fileSystem;
        this.reproducibleFileOrder = reproducibleFileOrder;
        this.documentationRegistry = documentationRegistry;
    }

    public WorkResult execute(final CopySpecInternal spec, CopyAction action) {
        final CopyAction effectiveVisitor = new DuplicateHandlingCopyActionDecorator(
                new NormalizingCopyActionDecorator(action, fileSystem),
                spec,
                documentationRegistry
        );

        CopyActionProcessingStream processingStream = new CopySpecBackedCopyActionProcessingStream(spec, instantiator, propertyFactory, fileSystem, reproducibleFileOrder);
        return effectiveVisitor.execute(processingStream);
    }

}
