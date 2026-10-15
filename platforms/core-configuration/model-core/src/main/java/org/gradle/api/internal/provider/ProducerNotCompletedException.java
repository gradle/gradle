/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableList;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Task;
import org.gradle.internal.exceptions.ResolutionProvider;

import java.util.List;

public class ProducerNotCompletedException extends InvalidUserCodeException implements ResolutionProvider {

    private final List<String> resolutions;

    public ProducerNotCompletedException(String derivedValueKind, ProviderInternal<?> provider, Task producer) {
        super(String.format(
            "Querying the %s value of %s before %s has completed is not supported. " +
                "The %s value may depend on the content produced by %s, so it can only be calculated after %s has completed.",
            derivedValueKind, provider, producer,
            derivedValueKind, producer, producer
        ));
        this.resolutions = ImmutableList.of(
            String.format("To use the value in another task, declare it as an input of that task, so that the task runs after %s.", producer),
            String.format("To use the value in the actions of %s, query the property with 'get()' and transform the result.", producer),
            "If only the location of a file or directory is needed, use the 'locationOnly' provider of the file property instead."
        );
    }

    @Override
    public List<String> getResolutions() {
        return resolutions;
    }
}
