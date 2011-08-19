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

import java.lang.annotation.Annotation
import java.lang.reflect.Field

import com.google.common.collect.Sets

import net.jcip.annotations.NotThreadSafe

@NotThreadSafe
class PropertyConverter {
    String propertyAnnotationKeyAttribute = "value"
    Class<? extends Annotation> includePropertiesAnnotationClass = null
    List<Closure> propertyProcessors = []
    boolean includeNullValues = false

    private final Object object
    private final Class<? extends Annotation> propertyAnnotationClass
    private final Set<Object> visitedObjects = Sets.newIdentityHashSet()

    PropertyConverter(Object object, Class<? extends Annotation> propertyAnnotationClass) {
        this.object = object
        this.propertyAnnotationClass = propertyAnnotationClass
    }

    Map<String, String> convertProperties() {
        visitedObjects.clear()
        def properties = collectProperties(object)
        processProperties(properties)
        properties
    }

    private Map<String, String> collectProperties(Object object) {
        Map<String, String> properties = [:]

        if (object == null || !visitedObjects.add(object)) {
            return properties
        }

        for (field in getAllFields(object.getClass())) {
            if (includePropertiesAnnotationClass != null &&
                    field.isAnnotationPresent(includePropertiesAnnotationClass)) {
                def propValue = object."$field.name"
                properties.putAll(collectProperties(propValue))
                continue
            }

            def propertyAnnotation = field.getAnnotation(propertyAnnotationClass)
            if (propertyAnnotation == null) {
                continue
            }

            def propStringValue = object."$field.name"?.toString()
            if (!includeNullValues && propStringValue == null) {
                continue
            }

            properties.put(propertyAnnotation."$propertyAnnotationKeyAttribute"(), propStringValue)
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
        def curr = clazz
        while (curr != null) {
            fields.addAll(curr.declaredFields)
            curr = curr.superclass
        }
        fields
    }
}
