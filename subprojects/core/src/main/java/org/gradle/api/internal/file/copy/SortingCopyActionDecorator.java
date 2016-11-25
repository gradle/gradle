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

package org.gradle.api.internal.file.copy;

import org.gradle.api.internal.file.CopyActionProcessingStreamAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class SortingCopyActionDecorator implements CopyActionProcessingStream  {
    private CopyActionProcessingStream stream;

    public SortingCopyActionDecorator(CopyActionProcessingStream stream) {
        this.stream = stream;
    }

    @Override
    public void process(final CopyActionProcessingStreamAction action) {
        final ArrayList<FileCopyDetailsInternal> files = new ArrayList<FileCopyDetailsInternal>();
        stream.process(new CopyActionProcessingStreamAction() {
            @Override
            public void processFile(FileCopyDetailsInternal details) {
                if (details.isRealFile()) {
                    files.add(details);
                } else {
                    action.processFile(details);
                }
            }
        });
        Collections.sort(files, new Comparator<FileCopyDetailsInternal>() {
            @Override
            public int compare(FileCopyDetailsInternal o1, FileCopyDetailsInternal o2) {
                return o1.getPath().compareTo(o2.getPath());
            }
        });
        for (FileCopyDetailsInternal file : files) {
            action.processFile(file);
        }
    }
}
