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

package org.gradle.tooling.events.problems.internal;

import com.google.common.collect.ImmutableMap;
import org.gradle.tooling.events.problems.AdditionalData;

import java.io.Serializable;
import java.util.Map;

public class DefaultAdditionalData implements AdditionalData, Serializable {

    private final Map<String, Object> additionalData;

    public DefaultAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = ImmutableMap.copyOf(additionalData);
    }

    @Override
    public Map<String, Object> getAsMap() {
        return additionalData;
    }
}
