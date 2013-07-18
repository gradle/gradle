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
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.WorkResult;

import java.util.HashSet;
import java.util.Set;

public class DuplicateHandlingCopyActionDecorator implements CopyAction {

    private final CopyAction delegate;

    private final Action<? super FileCopyDetails> onUnhandledDuplicate;

    public DuplicateHandlingCopyActionDecorator(CopyAction delegate, Action<? super FileCopyDetails> onUnhandledDuplicate) {
        this.delegate = delegate;
        this.onUnhandledDuplicate = onUnhandledDuplicate;
    }

    public WorkResult execute(final Action<Action<? super FileCopyDetailsInternal>> stream) {
        final Set<RelativePath> visitedFiles = new HashSet<RelativePath>();

        return delegate.execute(new Action<Action<? super FileCopyDetailsInternal>>() {
            public void execute(final Action<? super FileCopyDetailsInternal> delegateAction) {
                stream.execute(new Action<FileCopyDetailsInternal>() {
                    public void execute(FileCopyDetailsInternal details) {
                        if (!details.isDirectory()) {
                            DuplicatesStrategy strategy = details.getDuplicatesStrategy();

                            if (!visitedFiles.add(details.getRelativePath())) {
                                if (strategy == DuplicatesStrategy.EXCLUDE) {
                                    return;
                                }
                                if (strategy != DuplicatesStrategy.INCLUDE) {
                                    onUnhandledDuplicate.execute(details);
                                }
                            }
                        }

                        delegateAction.execute(details);
                    }
                });
            }
        });
    }
}
