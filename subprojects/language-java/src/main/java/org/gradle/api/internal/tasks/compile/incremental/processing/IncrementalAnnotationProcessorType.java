/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.processing;

import java.util.Locale;

/**
 * The different kinds of annotation processors that the incremental compiler knows how to handle.
 * See the user guide chapter on incremental annotation processing for more information.
 */
public enum IncrementalAnnotationProcessorType {
    ISOLATING,
    AGGREGATING,
    DYNAMIC,
    UNKNOWN;

    public String getProcessorOption() {
        return "org.gradle.annotation.processing." + name().toLowerCase(Locale.ROOT);
    }
}
