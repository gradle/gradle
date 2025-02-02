/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.ReproducibleFileVisitor;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

public class CopyFileVisitorImpl implements ReproducibleFileVisitor {
    private final CopySpecResolver copySpecResolver;
    private final CopyActionProcessingStreamAction action;
    private final Instantiator instantiator;
    private final PropertyFactory propertyFactory;
    private final FileSystem fileSystem;
    private final boolean reproducibleFileOrder;

    public CopyFileVisitorImpl(CopySpecResolver spec, CopyActionProcessingStreamAction action, Instantiator instantiator, PropertyFactory propertyFactory, FileSystem fileSystem,
                               boolean reproducibleFileOrder) {
        this.copySpecResolver = spec;
        this.action = action;
        this.instantiator = instantiator;
        this.propertyFactory = propertyFactory;
        this.fileSystem = fileSystem;
        this.reproducibleFileOrder = reproducibleFileOrder;
    }

    @Override
    public void visitDir(FileVisitDetails dirDetails) {
        processDir(dirDetails);
    }

    @Override
    public void visitFile(FileVisitDetails fileDetails) {
        processFile(fileDetails);
    }

    private void processDir(FileVisitDetails visitDetails) {
        DefaultFileCopyDetails details = createDefaultFileCopyDetails(visitDetails);
        action.processFile(details);
    }

    private void processFile(FileVisitDetails visitDetails) {
        DefaultFileCopyDetails details = createDefaultFileCopyDetails(visitDetails);
        for (Action<? super FileCopyDetails> action : copySpecResolver.getAllCopyActions()) {
            action.execute(details);
            if (details.isExcluded()) {
                return;
            }
        }
        action.processFile(details);
    }

    private DefaultFileCopyDetails createDefaultFileCopyDetails(FileVisitDetails visitDetails) {
        return instantiator.newInstance(DefaultFileCopyDetails.class, visitDetails, copySpecResolver, instantiator, propertyFactory, fileSystem);
    }

    @Override
    public boolean isReproducibleFileOrder() {
        return reproducibleFileOrder;
    }
}
