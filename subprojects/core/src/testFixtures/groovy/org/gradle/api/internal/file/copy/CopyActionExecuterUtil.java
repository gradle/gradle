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

import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.tasks.WorkResult;

import java.util.Arrays;

public class CopyActionExecuterUtil {

    public static WorkResult visit(CopyAction visitor, final Iterable<FileCopyDetailsInternal> details) {
        return visitor.execute(new CopyActionProcessingStream() {
            @Override
            public void process(CopyActionProcessingStreamAction action) {
                for (FileCopyDetailsInternal detailsInternal : details) {
                    action.processFile(detailsInternal);
                }
            }
        });
    }

    public static WorkResult visit(CopyAction visitor, final FileCopyDetailsInternal... details) {
        return visit(visitor, Arrays.asList(details));
    }

}
