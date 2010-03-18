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
package org.gradle.api.tasks;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.copy.FileCopyActionImpl;
import org.gradle.api.internal.file.copy.FileCopySpecVisitor;
import org.gradle.api.internal.file.copy.SyncCopySpecVisitor;
import org.gradle.api.internal.project.ProjectInternal;

import java.io.File;

/**
 * Task for synchronizing the contents of a directory.
 *
 */
public class Sync extends AbstractCopyTask {
    private FileCopyActionImpl action;

    public Sync() {
        FileResolver fileResolver = ((ProjectInternal) getProject()).getFileResolver();
        action = new FileCopyActionImpl(fileResolver, new SyncCopySpecVisitor(new FileCopySpecVisitor()));
    }

    @Override
    protected FileCopyActionImpl getCopyAction() {
        return action;
    }

    @OutputDirectory
    public File getDestinationDir() {
        return getCopyAction().getDestinationDir();
    }
}
