/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.internal.execution.ChangingFilesRunner;
import org.gradle.internal.execution.OutputChangeListener;

import java.util.function.Supplier;

public class DefaultChangingFilesRunner implements ChangingFilesRunner {

    private final OutputChangeListener outputChangeListener;

    public DefaultChangingFilesRunner(OutputChangeListener outputChangeListener) {
        this.outputChangeListener = outputChangeListener;
    }

    @Override
    public <T> T changeFiles(Iterable<String> changingLocations, Supplier<T> changeLocationsAction) {
        outputChangeListener.beforeOutputChange(changingLocations);
        try {
            return changeLocationsAction.get();
        } finally {
            outputChangeListener.beforeOutputChange(changingLocations);
        }
    }
}
