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

import java.io.Serializable;

public class DefaultTypeValidationData implements TypeValidationData, Serializable {

    private final String pluginId;
    private final String propertyName;
    private final String parentPropertyName;
    private final String typeName;

    public DefaultTypeValidationData(String pluginId, String propertyName, String parentPropertyName, String typeName) {
        this.pluginId = pluginId;
        this.propertyName = propertyName;
        this.parentPropertyName = parentPropertyName;
        this.typeName = typeName;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public String getParentPropertyName() {
        return parentPropertyName;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }

    public static DefaultTypeValidationDataBuilder builder() {
        return new DefaultTypeValidationDataBuilder();
    }

    public static AdditionalDataBuilder<TypeValidationData> builder(TypeValidationData from) {
        return new DefaultTypeValidationDataBuilder(from);
    }

    private static class DefaultTypeValidationDataBuilder implements TypeValidationDataSpec, AdditionalDataBuilder<TypeValidationData> {

        private String pluginId;
        private String propertyName;
        private String parentPropertyName;
        private String typeName;

        public DefaultTypeValidationDataBuilder() {
        }

        public DefaultTypeValidationDataBuilder(TypeValidationData from) {
            this.pluginId = from.getPluginId();
            this.propertyName = from.getPropertyName();
            this.parentPropertyName = from.getParentPropertyName();
            this.typeName = from.getTypeName();
        }

        @Override
        public DefaultTypeValidationData build() {
            return new DefaultTypeValidationData(pluginId, propertyName, parentPropertyName, typeName);
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
