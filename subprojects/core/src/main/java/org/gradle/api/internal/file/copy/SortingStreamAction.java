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

import com.google.common.base.Function;
import com.google.common.collect.Ordering;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SortingStreamAction implements CopyActionProcessingStreamAction, Flushable {

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
            flush();
            action.processFile(details);
        }
    }

    @Override
    public void flush() {
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
