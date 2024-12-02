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

@Nullable
public class DefaultTypeValidationData implements TypeValidationData, Serializable {

    private final String pluginId;
    private final String propertyName;
    private final String functionName;
    private final String parentPropertyName;
    private final String typeName;

    public DefaultTypeValidationData(
        @Nullable String pluginId,
        @Nullable String propertyName,
        @Nullable String functionName,
        @Nullable String parentPropertyName,
        @Nullable String typeName
    ) {
        this.pluginId = pluginId;
        this.propertyName = propertyName;
        this.functionName = functionName;
        this.parentPropertyName = parentPropertyName;
        this.typeName = typeName;
    }

    @Override
    @Nullable
    public String getPluginId() {
        return pluginId;
    }

    @Override
    @Nullable
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    @Nullable
    public String getFunctionName() {
        return functionName;
    }

    @Override
    @Nullable
    public String getParentPropertyName() {
        return parentPropertyName;
    }

    @Override
    @Nullable
    public String getTypeName() {
        return typeName;
    }

    public static AdditionalDataBuilder<TypeValidationData> builder(@Nullable TypeValidationData from) {
        if (from == null) {
            return new DefaultTypeValidationDataBuilder();
        }
        return new DefaultTypeValidationDataBuilder(from);
    }

    @Override
    public Map<String, Object> getAsMap() {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        if (pluginId != null) {
            builder.put("pluginId", pluginId);
        }
        if (propertyName != null) {
            builder.put("propertyName", propertyName);
        }
        if (functionName != null) {
            builder.put("functionName", functionName);
        }
        if (parentPropertyName != null) {
            builder.put("parentPropertyName", parentPropertyName);
        }
        if (typeName != null) {
            builder.put("typeName", typeName);
        }
        return builder.build();
    }

    @Nullable
    @Override
    public Object get() {
        return this;
    }

    private static class DefaultTypeValidationDataBuilder implements TypeValidationDataSpec, AdditionalDataBuilder<TypeValidationData> {

        @Nullable
        private String pluginId;
        @Nullable
        private String propertyName;
        @Nullable
        private String functionName;
        @Nullable
        private String parentPropertyName;
        @Nullable
        private String typeName;

        public DefaultTypeValidationDataBuilder() {
        }

        public DefaultTypeValidationDataBuilder(TypeValidationData from) {
            this.pluginId = from.getPluginId();
            this.propertyName = from.getPropertyName();
            this.functionName = from.getFunctionName();
            this.parentPropertyName = from.getParentPropertyName();
            this.typeName = from.getTypeName();
        }

        @Override
        public DefaultTypeValidationData build() {
            return new DefaultTypeValidationData(pluginId, propertyName, functionName, parentPropertyName, typeName);
        }

        @Override
        public TypeValidationDataSpec pluginId(String pluginId) {
            this.pluginId = pluginId;
            return this;
        }

        @Override
        public TypeValidationDataSpec propertyName(String propertyName) {
            this.propertyName = propertyName;
            return this;
        }

        @Override
        public TypeValidationDataSpec functionName(String functionName) {
            this.functionName = functionName;
            return this;
        }

        @Override
        public TypeValidationDataSpec parentPropertyName(String parentPropertyName) {
            this.parentPropertyName = parentPropertyName;
            return this;
        }

        @Override
        public TypeValidationDataSpec typeName(String typeName) {
            this.typeName = typeName;
            return this;
        }
    }
}
