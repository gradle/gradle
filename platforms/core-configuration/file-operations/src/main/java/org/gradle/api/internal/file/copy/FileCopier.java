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

import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

public class FileCopier {
    private final Deleter deleter;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileCollectionFactory fileCollectionFactory;
    private final FileResolver fileResolver;
    private final Factory<PatternSet> patternSetFactory;
    private final PropertyFactory propertyFactory;
    private final FileSystem fileSystem;
    private final Instantiator instantiator;
    private final DocumentationRegistry documentationRegistry;

    public FileCopier(
        Deleter deleter,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        FileCollectionFactory fileCollectionFactory,
        FileResolver fileResolver,
        Factory<PatternSet> patternSetFactory,
        PropertyFactory propertyFactory,
        FileSystem fileSystem,
        Instantiator instantiator,
        DocumentationRegistry documentationRegistry
    ) {
        this.deleter = deleter;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileResolver = fileResolver;
        this.patternSetFactory = patternSetFactory;
        this.propertyFactory = propertyFactory;
        this.fileSystem = fileSystem;
        this.instantiator = instantiator;
        this.documentationRegistry = documentationRegistry;
    }

    private DestinationRootCopySpec createCopySpec(Action<? super SyncSpec> action) {
        DefaultCopySpec copySpec = new DefaultCopySpec(fileCollectionFactory, propertyFactory, instantiator, patternSetFactory);
        DestinationRootCopySpec destinationRootCopySpec = new DestinationRootCopySpec(fileResolver, copySpec);
        SyncSpec wrapped = instantiator.newInstance(CopySpecWrapper.class, destinationRootCopySpec);
        action.execute(wrapped);
        return destinationRootCopySpec;
    }

    public WorkResult copy(Action<? super CopySpec> action) {
        DestinationRootCopySpec copySpec = createCopySpec(action);
        File destinationDir = copySpec.getDestinationDir();
        return doCopy(copySpec, getCopyVisitor(destinationDir));
    }

    public WorkResult sync(Action<? super SyncSpec> action) {
        DestinationRootCopySpec copySpec = createCopySpec(action);
        File destinationDir = copySpec.getDestinationDir();
        return doCopy(copySpec,
            new SyncCopyActionDecorator(
                destinationDir,
                getCopyVisitor(destinationDir),
                copySpec.getPreserve(),
                deleter,
                directoryFileTreeFactory
            )
        );
    }

    private FileCopyAction getCopyVisitor(File destination) {
        return new FileCopyAction(fileResolver.newResolver(destination));
    }

    private WorkResult doCopy(CopySpecInternal copySpec, CopyAction visitor) {
        CopyActionExecuter visitorDriver = new CopyActionExecuter(instantiator, propertyFactory, fileSystem, false, documentationRegistry);
        return visitorDriver.execute(copySpec, visitor);
    }

}
