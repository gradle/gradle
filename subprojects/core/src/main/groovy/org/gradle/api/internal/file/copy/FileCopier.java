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
import org.gradle.api.internal.Actions;
import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeplatform.filesystem.FileSystems;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

public class FileCopier {

    private final Instantiator instantiator;
    private final FileResolver fileResolver;

    public FileCopier(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        this.fileResolver = fileResolver;
    }

    private DestinationRootCopySpec createCopySpec(Action<? super CopySpec> action) {
        DefaultCopySpec copySpec = instantiator.newInstance(DefaultCopySpec.class, this.fileResolver, instantiator);
        DestinationRootCopySpec destinationRootCopySpec = instantiator.newInstance(DestinationRootCopySpec.class, fileResolver, copySpec);
        action.execute(destinationRootCopySpec);
        return destinationRootCopySpec;
    }

    public WorkResult copy(Action<? super CopySpec> action) {
        DestinationRootCopySpec copySpec = createCopySpec(action);
        File destinationDir = copySpec.getDestinationDir();
        return doCopy(copySpec, getCopyVisitor(destinationDir));
    }

    public WorkResult sync(Action<? super CopySpec> action) {
        DestinationRootCopySpec copySpec = createCopySpec(action);
        File destinationDir = copySpec.getDestinationDir();
        return doCopy(copySpec, new SyncCopyActionDecorator(destinationDir, getCopyVisitor(destinationDir)));
    }

    private FileCopyAction getCopyVisitor(File destination) {
        return new FileCopyAction(new BaseDirFileResolver(destination));
    }

    private WorkResult doCopy(CopySpecInternal copySpec, CopyAction visitor) {
        CopyActionExecuter visitorDriver = new CopyActionExecuter(instantiator, FileSystems.getDefault(), Actions.doNothing());
        return visitorDriver.execute(copySpec, visitor);
    }

}
