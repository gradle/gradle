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
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

public class CopySpecBackCopyActionProcessingStream implements CopyActionProcessingStream {

    private final CopySpecInternal spec;
    private final Instantiator instantiator;
    private final FileSystem fileSystem;

    public CopySpecBackCopyActionProcessingStream(CopySpecInternal spec, Instantiator instantiator, FileSystem fileSystem) {
        this.spec = spec;
        this.instantiator = instantiator;
        this.fileSystem = fileSystem;
    }

    public void process(final Action<? super FileCopyDetailsInternal> action) {
        spec.walk(new Action<CopySpecInternal>() {
            public void execute(final CopySpecInternal spec) {
                FileTree source = spec.getSource();
                source.visit(new FileVisitor() {
                    public void visitDir(FileVisitDetails dirDetails) {
                        visit(dirDetails);
                    }

                    public void visitFile(FileVisitDetails fileDetails) {
                        visit(fileDetails);
                    }

                    private void visit(FileVisitDetails visitDetails) {
                        DefaultFileCopyDetails details = instantiator.newInstance(DefaultFileCopyDetails.class, visitDetails, spec, fileSystem);
                        for (Action<? super FileCopyDetails> action : spec.getAllCopyActions()) {
                            action.execute(details);
                            if (details.isExcluded()) {
                                return;
                            }
                        }
                        action.execute(details);
                    }
                });
            }
        });

    }
}
