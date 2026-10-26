/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.Incubating;
import org.gradle.api.problems.AdditionalData;

import java.util.Map;

/**
 * General additional data type that can be used to attach arbitrary data to a problem with a string map.
 *
 * @since 8.13
 */
@Incubating
public interface GeneralData extends AdditionalData {

    /**
     * Return the data as a map of strings.
     *
     * @return the data
     * @since 8.13
     */
    Map<String, String> getAsMap();
}
