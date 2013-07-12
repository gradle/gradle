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
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

public class CopySpecContentVisitorDriver {

    private final Instantiator instantiator;
    private final FileSystem fileSystem;

    public CopySpecContentVisitorDriver(Instantiator instantiator, FileSystem fileSystem) {
        this.instantiator = instantiator;
        this.fileSystem = fileSystem;
    }

    void visit(CopyAction copyAction, CopySpecInternal toVisit, final CopySpecContentVisitor visitor) {
        visitor.startVisit(copyAction);
        toVisit.visit(new Action<CopySpecInternal>() {
            public void execute(final CopySpecInternal spec) {
                visitor.visitSpec(spec);
                FileTree source = spec.getSource();
                source.visit(new FileVisitor() {
                    public void visitDir(FileVisitDetails dirDetails) {
                        visitor.visitDir(new DefaultFileCopyDetails(dirDetails, spec, fileSystem));
                    }

                    public void visitFile(FileVisitDetails fileDetails) {
                        DefaultFileCopyDetails details = instantiator.newInstance(DefaultFileCopyDetails.class, fileDetails, spec, fileSystem);
                        for (Action<? super FileCopyDetails> action : spec.getAllCopyActions()) {
                            action.execute(details);
                            if (details.isExcluded()) {
                                return;
                            }
                        }
                        visitor.visitFile(details);
                    }
                });
            }
        });
        visitor.endVisit();
    }

}