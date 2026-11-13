/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.problems.AdditionalData;

/**
 * Additional data for a {@link org.gradle.internal.configuration.problems.PropertyTrace} .
 * Currently, this is only a String, but it could be extended as needed in the future.
 *
 * These data classes are part of the concept used for adding structured additional data to problems.
 */
public interface PropertyTraceData extends AdditionalData {
    String getTrace();
}
