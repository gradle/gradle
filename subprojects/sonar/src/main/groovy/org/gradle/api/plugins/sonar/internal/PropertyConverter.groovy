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
package org.gradle.api.plugins.sonar.internal

import java.lang.annotation.Annotation

class PropertyConverter {
    GroovyObject object

    PropertyConverter(GroovyObject object) {
        this.object = object
    }

    Map<String, String> convertProperties(Class<? extends Annotation> annotationClass, List<Closure> propertyProcessors) {
        def properties = collectProperties(annotationClass)
        processProperties(properties, propertyProcessors)
    }

    private Map<String, String> collectProperties(Class<? extends Annotation> annotationClass) {
        Map<String, String> properties = [:]

        for (field in object.getClass().declaredFields) {
            def annotation = field.getAnnotation(annotationClass)
            if (!annotation) continue

            properties.put(annotation.value(), object.getProperty(field.name).toString())
        }

        properties
    }

    private Map<String, String> processProperties(Map<String, String> properties, List<Closure> propertyProcessors) {
        //noinspection GroovyAssignabilityCheck
        propertyProcessors.inject(properties) { props, processor -> processor(props) }
    }
}
