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

import org.gradle.api.InvalidUserDataException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class GetInputPropertiesVisitor extends PropertyVisitor.Adapter {
    private final String beanName;
    private final List<InputPropertySpec> inputProperties = new ArrayList<>();

    public GetInputPropertiesVisitor(String beanName) {
        this.beanName = beanName;
    }

    @Override
    public void visitInputProperty(String propertyName, PropertyValue value, boolean optional) {
        InputPropertySpec spec = new DefaultInputPropertySpec(propertyName, value);
        inputProperties.add(spec);
    }

    public Supplier<Map<String, Object>> getPropertyValuesSupplier() {
        return () -> {
            Map<String, Object> result = new HashMap<>();
            for (InputPropertySpec inputProperty : inputProperties) {
                String propertyName = inputProperty.getPropertyName();
                try {
                    Object value = InputParameterUtils.prepareInputParameterValue(inputProperty.getValue());
                    result.put(propertyName, value);
                } catch (Exception ex) {
                    throw new InvalidUserDataException(String.format("Error while evaluating property '%s' of %s", propertyName, beanName), ex);
                }
            }
            return result;
        };
    }
}
