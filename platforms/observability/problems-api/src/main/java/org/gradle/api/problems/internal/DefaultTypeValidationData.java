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

    private final String pluginId;
    private final String propertyName;
    private final String functionName;
    private final String parentPropertyName;
    private final String typeName;
    @Nullable
    private final String declaringClassName;
    @Nullable
    private final String sourceFilename;
    @Nullable
    private final Integer lineNumber;

    public DefaultTypeValidationData(String pluginId, String propertyName, String functionName, String parentPropertyName, String typeName) {
        this(pluginId, propertyName, functionName, parentPropertyName, typeName, null, null, null);
    }

    public DefaultTypeValidationData(
        @Nullable String pluginId,
        @Nullable String propertyName,
        @Nullable String functionName,
        @Nullable String parentPropertyName,
        @Nullable String typeName,
        @Nullable String declaringClassName,
        @Nullable String sourceFilename,
        @Nullable Integer lineNumber
    ) {
        this.pluginId = pluginId;
        this.propertyName = propertyName;
        this.functionName = functionName;
        this.parentPropertyName = parentPropertyName;
        this.typeName = typeName;
        this.declaringClassName = declaringClassName;
        this.sourceFilename = sourceFilename;
        this.lineNumber = lineNumber;
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
    public String getFunctionName() {
        return functionName;
    }

    @Override
    public String getParentPropertyName() {
        return parentPropertyName;
    }

    @Override
    public String getTypeName() {
        return typeName;
    }

    @Override
    @Nullable
    public String getDeclaringClassName() {
        return declaringClassName;
    }

    @Override
    @Nullable
    public String getSourceFilename() {
        return sourceFilename;
    }

    @Override
    @Nullable
    public Integer getLineNumber() {
        return lineNumber;
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
            equal(typeName, that.typeName) &&
            equal(declaringClassName, that.declaringClassName) &&
            equal(sourceFilename, that.sourceFilename) &&
            equal(lineNumber, that.lineNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(pluginId, propertyName, functionName, parentPropertyName, typeName, declaringClassName, sourceFilename, lineNumber);
    }

    public static AdditionalDataBuilder<TypeValidationData> builder(@Nullable TypeValidationData from) {
        if(from == null) {
            return new DefaultTypeValidationDataBuilder();
        }
        return new DefaultTypeValidationDataBuilder(from);
    }

    private static class DefaultTypeValidationDataBuilder implements TypeValidationDataSpec, AdditionalDataBuilder<TypeValidationData> {

        private String pluginId;
        private String propertyName;
        private String functionName;
        private String parentPropertyName;
        private String typeName;
        private String declaringClassName;
        private String sourceFilename;
        private Integer lineNumber;

        public DefaultTypeValidationDataBuilder() {
        }

        public DefaultTypeValidationDataBuilder(TypeValidationData from) {
            this.pluginId = from.getPluginId();
            this.propertyName = from.getPropertyName();
            this.functionName = from.getFunctionName();
            this.parentPropertyName = from.getParentPropertyName();
            this.typeName = from.getTypeName();
            this.declaringClassName = from.getDeclaringClassName();
            this.sourceFilename = from.getSourceFilename();
            this.lineNumber = from.getLineNumber();
        }

        @Override
        public DefaultTypeValidationData build() {
            return new DefaultTypeValidationData(pluginId, propertyName, functionName, parentPropertyName, typeName, declaringClassName, sourceFilename, lineNumber);
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

        @Override
        public TypeValidationDataSpec declaringClassName(@Nullable String declaringClassName) {
            this.declaringClassName = declaringClassName;
            return this;
        }

        @Override
        public TypeValidationDataSpec sourceFilename(@Nullable String sourceFilename) {
            this.sourceFilename = sourceFilename;
            return this;
        }

        @Override
        public TypeValidationDataSpec lineNumber(@Nullable Integer lineNumber) {
            this.lineNumber = lineNumber;
            return this;
        }
    }
}
