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
