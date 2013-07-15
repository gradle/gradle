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

import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeplatform.filesystem.FileSystems;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

public class FileCopyAction {

    private final Instantiator instantiator;
    private final DestinationRootCopySpec copySpec;

    public FileCopyAction(Instantiator instantiator, FileResolver fileResolver) {
        this.instantiator = instantiator;
        DefaultCopySpec copySpec = instantiator.newInstance(DefaultCopySpec.class, fileResolver, instantiator);
        this.copySpec = instantiator.newInstance(DestinationRootCopySpec.class, fileResolver, copySpec);
    }

    public CopySpecInternal getCopySpec() {
        return copySpec;
    }

    public WorkResult copy() {
        return doCopy(getCopyVisitor(getDestination()));
    }

    public WorkResult sync() {
        File destination = getDestination();
        return doCopy(new SyncCopySpecContentVisitor(getCopyVisitor(destination), destination));
    }

    private FileCopySpecContentVisitor getCopyVisitor(File destination) {
        return new FileCopySpecContentVisitor(new BaseDirFileResolver(destination));
    }

    private WorkResult doCopy(CopySpecContentVisitor visitor) {
        CopySpecContentVisitorDriver visitorDriver = new CopySpecContentVisitorDriver(instantiator, FileSystems.getDefault());
        visitorDriver.visit(copySpec, visitor);
        return new SimpleWorkResult(visitor.getDidWork());
    }

    private File getDestination() {
        return copySpec.getDestinationDir();
    }

}
