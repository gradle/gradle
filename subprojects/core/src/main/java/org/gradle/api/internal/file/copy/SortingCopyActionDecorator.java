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
import org.gradle.api.tasks.WorkResult;

public class SortingCopyActionDecorator implements CopyAction {
    private final CopyAction delegate;

    public SortingCopyActionDecorator(CopyAction delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {
        return delegate.execute(new CopyActionProcessingStream() {
            @Override
            public void process(CopyActionProcessingStreamAction action) {
                stream.process(new SortingStreamAction(action));
            }
        });
    }
}
