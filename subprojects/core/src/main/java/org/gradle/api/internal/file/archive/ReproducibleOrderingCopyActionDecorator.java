/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.file.archive;

import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.copy.CopyAction;
import org.gradle.api.internal.file.copy.CopyActionProcessingStream;
import org.gradle.api.internal.file.copy.FileCopyDetailsInternal;
import org.gradle.api.tasks.WorkResult;

import java.util.Comparator;
import java.util.Map;

public class ReproducibleOrderingCopyActionDecorator implements CopyAction {
    private final CopyAction delegate;
    private final Comparator<RelativePath> comparator;

    public ReproducibleOrderingCopyActionDecorator(CopyAction delegate) {
        this(delegate, Ordering.<RelativePath>natural());
    }

    public ReproducibleOrderingCopyActionDecorator(CopyAction delegate, Comparator<RelativePath> comparator) {
        this.delegate = delegate;
        this.comparator = comparator;
    }

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {
        return delegate.execute(new CopyActionProcessingStream() {
            public void process(final CopyActionProcessingStreamAction action) {
                // Sort all of the files going into the underlying CopyAction
                final Map<RelativePath, FileCopyDetailsInternal> files = Maps.newTreeMap(comparator);
                stream.process(new CopyActionProcessingStreamAction() {
                    public void processFile(FileCopyDetailsInternal details) {
                        files.put(details.getRelativePath(), details);
                    }
                });
                // Call the underlying CopyAction with the sorted collection of files
                for (FileCopyDetailsInternal fileCopyDetailsInternal : files.values()) {
                    action.processFile(fileCopyDetailsInternal);
                }
            }
        });
    }
}
