/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.changedetection.state.OutputPathNormalizationStrategy;
import org.gradle.api.internal.changedetection.state.PathNormalizationStrategy;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

import static org.gradle.api.internal.tasks.TaskPropertyUtils.checkPropertyName;

@NonNullApi
abstract class AbstractTaskOutputPropertySpec extends TaskOutputsDeprecationSupport implements TaskPropertySpec, TaskOutputFilePropertyBuilder {

    private String propertyName;
    private boolean optional;

    @Override
    public TaskOutputFilePropertyBuilder withPropertyName(String propertyName) {
        this.propertyName = checkPropertyName(propertyName);
        return this;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    protected boolean isOptional() {
        return optional;
    }

    @Override
    public TaskOutputFilePropertyBuilder optional() {
        return optional(true);
    }

    @Override
    public TaskOutputFilePropertyBuilder optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    public PathNormalizationStrategy getPathNormalizationStrategy() {
        return OutputPathNormalizationStrategy.getInstance();
    }

    @Override
    public String toString() {
        return getPropertyName() + " (OUTPUT)";
    }

    public Class<? extends FileNormalizer> getNormalizer() {
        return GenericFileNormalizer.class;
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }
}
