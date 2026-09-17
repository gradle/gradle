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

import com.google.common.base.Objects;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

import static com.google.common.base.Objects.equal;

public class DefaultTypeValidationData implements TypeValidationData, Serializable {

    private final @Nullable String pluginId;
    private final @Nullable String propertyName;
    private final @Nullable String functionName;
    private final @Nullable String parentPropertyName;
    private final @Nullable String typeName;

    public DefaultTypeValidationData(@Nullable String pluginId, @Nullable String propertyName, @Nullable String functionName, @Nullable String parentPropertyName, @Nullable String typeName) {
        this.pluginId = pluginId;
        this.propertyName = propertyName;
        this.functionName = functionName;
        this.parentPropertyName = parentPropertyName;
        this.typeName = typeName;
    }

    @Nullable
    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Nullable
    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Nullable
    @Override
    public String getFunctionName() {
        return functionName;
    }

    @Nullable
    @Override
    public String getParentPropertyName() {
        return parentPropertyName;
    }

    @Nullable
    @Override
    public String getTypeName() {
        return typeName;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DefaultTypeValidationData)) {
            return false;
        }
        DefaultTypeValidationData that = (DefaultTypeValidationData) o;
        return equal(pluginId, that.pluginId) &&
            equal(propertyName, that.propertyName) &&
            equal(functionName, that.functionName) &&
            equal(parentPropertyName, that.parentPropertyName) &&
            equal(typeName, that.typeName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pluginId, propertyName, functionName, parentPropertyName, typeName);
    }

    public static AdditionalDataBuilder<TypeValidationData> builder(@Nullable TypeValidationData from) {
        if(from == null) {
            return new DefaultTypeValidationDataBuilder();
        }
        return new DefaultTypeValidationDataBuilder(from);
    }

    private static class DefaultTypeValidationDataBuilder implements TypeValidationDataSpec, AdditionalDataBuilder<TypeValidationData> {

        private @Nullable String pluginId;
        private @Nullable String propertyName;
        private @Nullable String functionName;
        private @Nullable String parentPropertyName;
        private @Nullable String typeName;

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
