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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.problems.AdditionalDataBuilder;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Map;

public class DefaultGeneralData implements GeneralData, Serializable {

    private final Map<String, String> map;

    public DefaultGeneralData(Map<String, String> map) {
        this.map = ImmutableMap.copyOf(map);
    }

    @Override
    public Map<String, String> getAsMap() {
        return map;
    }

    public static AdditionalDataBuilder<GeneralData> builder(@Nullable GeneralData from) {
        if(from == null) {
            return new DefaultGeneralDataBuilder();
        }
        return new DefaultGeneralDataBuilder(from);
    }

    private static class DefaultGeneralDataBuilder implements GeneralDataSpec, AdditionalDataBuilder<GeneralData> {
        private final ImmutableMap.Builder<String, String> mapBuilder = ImmutableMap.builder();

        private DefaultGeneralDataBuilder() {
        }

        private DefaultGeneralDataBuilder(GeneralData from) {
            mapBuilder.putAll(from.getAsMap());
        }

        @Override
        public GeneralDataSpec put(String key, String value) {
            mapBuilder.put(key, value);
            return this;
        }

        @Override
        public GeneralData build() {
            return new DefaultGeneralData(mapBuilder.build());
        }
    }
}
