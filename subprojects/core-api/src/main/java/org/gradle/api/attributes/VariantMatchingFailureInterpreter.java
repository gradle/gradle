/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;

import java.util.List;
import java.util.Optional;

/**
 * TODO: describe
 *
 * @since 8.4
 */
@Incubating
public interface VariantMatchingFailureInterpreter {
    // TODO: remove default
    default Optional<String> process(String producerDisplayName, HasAttributes requested, List<? extends HasAttributes> candidates) {
        return Optional.empty(); // default is failure to process
    }
}
