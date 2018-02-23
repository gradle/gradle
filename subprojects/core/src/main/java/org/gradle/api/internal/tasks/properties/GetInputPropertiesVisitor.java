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

package org.gradle.api.internal.tasks.properties;

import groovy.lang.GString;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.TaskInputPropertySpec;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class GetInputPropertiesVisitor extends PropertyVisitor.Adapter {
    private final String beanName;
    private List<TaskInputPropertySpec> inputProperties = new ArrayList<TaskInputPropertySpec>();

    public GetInputPropertiesVisitor(String beanName) {
        this.beanName = beanName;
    }

    @Override
    public void visitInputProperty(TaskInputPropertySpec inputProperty) {
        inputProperties.add(inputProperty);
    }

    public Factory<Map<String, Object>> getPropertyValuesFactory() {
        return new Factory<Map<String, Object>>() {
            @Override
            public Map<String, Object> create() {
                Map<String, Object> result = new HashMap<String, Object>();
                for (TaskInputPropertySpec inputProperty : inputProperties) {
                    String propertyName = inputProperty.getPropertyName();
                    try {
                        Object value = prepareValue(inputProperty.getValue());
                        result.put(propertyName, value);
                    } catch (Exception ex) {
                        throw new InvalidUserDataException(String.format("Error while evaluating property '%s' of %s", propertyName, beanName), ex);
                    }
                }
                return result;
            }
        };
    }

    @Nullable
    private static Object prepareValue(@Nullable Object value) {
        while (true) {
            if (value instanceof Callable) {
                Callable callable = (Callable) value;
                value = uncheckedCall(callable);
            } else if (value instanceof FileCollection) {
                FileCollection fileCollection = (FileCollection) value;
                return fileCollection.getFiles();
            } else {
                return avoidGString(value);
            }
        }
    }

    @Nullable
    private static Object avoidGString(@Nullable Object value) {
        return (value instanceof GString) ? value.toString() : value;
    }
}
