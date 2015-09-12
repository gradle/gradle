/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins.quality.internal.forking;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class DefaultAntResult implements AntResult, Serializable {
    private final int errorCount;
    private final Exception exception;
    private final Map<String, Object> projectProperties;

    public DefaultAntResult(int errorCount, Exception exception, Map<String, Object> antProperties) {
        this.errorCount = errorCount;
        this.exception = exception;
        this.projectProperties = antProperties == null ? null : Collections.unmodifiableMap(antProperties);
    }

    public DefaultAntResult(Map<String, Object> antProperties) {
        this(0, null, antProperties);
    }

    @Override
    public int getErrorCount() {
        return errorCount;
    }

    @Override
    public Exception getException() {
        return exception;
    }

    @Override
    public Map<String, Object> getProjectProperties() {
        return projectProperties;
    }
}
