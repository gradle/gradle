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
    private final String propertyPrefix

    ModelToPropertiesConverter(Object model, String propertyPrefix = null) {
        this.model = model
        this.propertyPrefix = propertyPrefix
    }

    Map<String, String> convert() {
        processProperties(collectProperties(model))
    }

    private Map<String, String> collectProperties(Object model) {
        def properties = [:]

        if (model == null) {
            return properties
        }

        for (field in getAllFields(model.getClass())) {
            if (field.isAnnotationPresent(IncludeProperties)) {
                def subModel = model."$field.name"
                properties.putAll(collectProperties(subModel))
                continue
            }

            def propAnnotation = field.getAnnotation(SonarProperty)
            if (propAnnotation == null) { continue }

            def propKey = propAnnotation.value()
            def propValue = model."$field.name"
            if (propValue == null) { continue }

            // keys are only converted after property processors have run
            properties.put(propKey, convertPropertyValue(propValue))
        }

        properties
    }

    private List<Field> getAllFields(Class<?> clazz) {
        def fields = []
        while (clazz != null) {
            fields.addAll(clazz.declaredFields)
            clazz = clazz.superclass
        }
        fields
    }

    private Map<String, String> processProperties(Map<String, String> properties) {
        for (processor in propertyProcessors) {
            processor(properties)
        }
        def result = [:]
        properties.each { key, value ->
            // for convenience, we also convert values, in case a processor has added non-string values
            result[convertPropertyKey(key)] = convertPropertyValue(value)
        }
        result
    }

    private String convertPropertyKey(String key) {
        propertyPrefix ? propertyPrefix + "." + key : key
    }

    private String convertPropertyValue(Object value) {
        if (value instanceof Collection) {
            value = value.join(",")
        }
        value.toString()
    }
}
