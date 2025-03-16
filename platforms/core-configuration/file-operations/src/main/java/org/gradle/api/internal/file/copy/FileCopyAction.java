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

import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.internal.FileUtils;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;
import java.util.Objects;

public class FileCopyAction implements CopyAction {

    private final PathToFileResolver fileResolver;

    public FileCopyAction(PathToFileResolver fileResolver) {
        this.fileResolver = fileResolver;
    }

    @Override
    public WorkResult execute(CopyActionProcessingStream stream) {
        FileCopyDetailsInternalAction action = new FileCopyDetailsInternalAction();
        stream.process(action);
        return WorkResults.didWork(action.didWork);
    }

    private class FileCopyDetailsInternalAction implements CopyActionProcessingStreamAction {
        private boolean didWork;

        @Override
        public void processFile(FileCopyDetailsInternal details) {
            File target = fileResolver.resolve(details.getRelativePath().getPathString());
            renameIfCaseChanged(target);
            boolean copied = details.copyTo(target);
            if (copied) {
                didWork = true;
            }
        }

        private void renameIfCaseChanged(File target) {
            if (target.exists()) {
                File canonicalizedTarget = FileUtils.canonicalize(target);
                if (!Objects.equals(target.getName(), canonicalizedTarget.getName())) {
                    canonicalizedTarget.renameTo(target);
                }
            }
        }
    }
}
