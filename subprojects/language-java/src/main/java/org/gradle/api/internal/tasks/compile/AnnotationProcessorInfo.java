/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import java.util.Map;

/**
 * Encapsulates information computed for each discovered annotation processor.
 */
public class AnnotationProcessorInfo {

    static final String NAME_KEY = "name";
    static final String PROCESSOR_KEY = "processor";
    static final String INCREMENTAL_KEY = "incremental";
    static final String UNKNOWN_NAME = "(unknown processor)";

    private Map<String, String> properties;

    AnnotationProcessorInfo(Map<String, String> data) {
        properties = data;
    }

    /**
     * True if the annotation processor adheres to the INCAP incremental AP spec.
     */
    public boolean isIncrementalEnabled() {
        String value = properties.get(INCREMENTAL_KEY);
        return value == null ? false : Boolean.valueOf(value);
    }

    /**
     * Returns a user-presentable name for the processor.
     */
    public String getName() {
        return properties.get(NAME_KEY);
    }

    /**
     * Returns true if processor services were found in this file.
     */
    public boolean isProcessor() {
        String value = properties.get(PROCESSOR_KEY);
        return value == null ? false : Boolean.valueOf(value);
    }
}
