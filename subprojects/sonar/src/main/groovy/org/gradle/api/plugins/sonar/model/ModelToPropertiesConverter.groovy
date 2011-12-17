/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.sonar.model

import java.lang.reflect.Field

import net.jcip.annotations.NotThreadSafe

/**
 * Converts a model object to a map of Sonar properties, guided by the information
 * provided with <tt>SonarProperty</tt> and <tt>IncludeProperties</tt> annotations.
 */
@NotThreadSafe
class ModelToPropertiesConverter {
    List<Closure> propertyProcessors = []

    private final Object model

    ModelToPropertiesConverter(Object model) {
        this.model = model
    }

    Map<String, String> convert() {
        def properties = collectProperties(model)
        processProperties(properties)
        properties
    }

    private Map<String, String> collectProperties(Object model) {
        Map<String, String> properties = [:]

        if (model == null) {
            return properties
        }

        for (field in getAllFields(model.getClass())) {
            if (field.isAnnotationPresent(IncludeProperties)) {
                def propValue = model."$field.name"
                properties.putAll(collectProperties(propValue))
                continue
            }

            def propertyAnnotation = field.getAnnotation(SonarProperty)
            if (propertyAnnotation == null) {
                continue
            }

            def propStringValue = model."$field.name"?.toString()
            if (propStringValue == null) {
                continue
            }

            properties.put(propertyAnnotation.value(), propStringValue)
        }

        properties
    }

    private void processProperties(Map<String, String> properties) {
        for (processor in propertyProcessors) {
            processor(properties)
        }
    }

    private List<Field> getAllFields(Class<?> clazz) {
        def fields = []
        while (clazz != null) {
            fields.addAll(clazz.declaredFields)
            clazz = clazz.superclass
        }
        fields
    }
}
