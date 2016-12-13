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

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import org.gradle.api.Action;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.Instantiator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CopySpecActionImpl implements Action<CopySpecResolver> {
    private final CopyActionProcessingStreamAction action;
    private final Instantiator instantiator;
    private final FileSystem fileSystem;
    private final boolean reproducibleFileOrder;

    public CopySpecActionImpl(CopyActionProcessingStreamAction action, Instantiator instantiator, FileSystem fileSystem, boolean reproducibleFileOrder) {
        this.action = action;
        this.instantiator = instantiator;
        this.fileSystem = fileSystem;
        this.reproducibleFileOrder = reproducibleFileOrder;
    }

    public void execute(final CopySpecResolver specResolver) {
        if (reproducibleFileOrder) {
            SortingStreamAction sortingStreamAction = new SortingStreamAction(action);
            visitSpec(sortingStreamAction, specResolver);
            sortingStreamAction.sort();
        } else {
            visitSpec(action, specResolver);
        }
    }

    private void visitSpec(CopyActionProcessingStreamAction action, CopySpecResolver specResolver) {
        FileTree source = specResolver.getSource();
        source.visit(new CopyFileVisitorImpl(specResolver, action, instantiator, fileSystem));
    }

    public static class SortingStreamAction implements CopyActionProcessingStreamAction {

        List<FileCopyDetailsInternal> copyDetailsInternals = new ArrayList<FileCopyDetailsInternal>();
        private CopyActionProcessingStreamAction action;

        public SortingStreamAction(CopyActionProcessingStreamAction action) {
            this.action = action;
        }

        @Override
        public void processFile(FileCopyDetailsInternal details) {
            if (details.isPhysicalFile()) {
                copyDetailsInternals.add(details);
            } else {
                sort();
                action.processFile(details);
            }
        }

        public void sort() {
            if (!copyDetailsInternals.isEmpty()) {
                Collections.sort(copyDetailsInternals, Ordering.natural().onResultOf(new Function<FileCopyDetailsInternal, Comparable>() {
                    @Override
                    public Comparable apply(FileCopyDetailsInternal input) {
                        return input.getPath();
                    }
                }));
                for (FileCopyDetailsInternal copyDetailsInternal : copyDetailsInternals) {
                    action.processFile(copyDetailsInternal);
                }
                copyDetailsInternals.clear();
            }
        }
    }
}
