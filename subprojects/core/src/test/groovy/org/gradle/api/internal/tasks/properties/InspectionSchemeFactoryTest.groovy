/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.properties

import org.gradle.api.internal.tasks.properties.annotations.NoOpPropertyAnnotationHandler
import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.InstantiationScheme
import spock.lang.Specification

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class InspectionSchemeFactoryTest extends Specification {
    def handler1 = handler(Thing1)
    def handler2 = handler(Thing2)
    def factory = new InspectionSchemeFactory([handler1, handler2], [], new TestCrossBuildInMemoryCacheFactory())

    def "creates inspection scheme that understands given property annotations and injection annotations"() {
        def instantiationScheme = Stub(InstantiationScheme)
        instantiationScheme.injectionAnnotations >> [InjectThing]
        def scheme = factory.inspectionScheme([Thing1, Thing2], instantiationScheme)

        expect:
        def metadata = scheme.metadataStore.getTypeMetadata(AnnotatedBean)
        def problems = []
        metadata.collectValidationFailures(null, new DefaultParameterValidationContext(problems))
        problems.empty
        metadata.propertiesMetadata.size() == 3

        def properties = metadata.propertiesMetadata.groupBy { it.propertyName }
        metadata.getAnnotationHandlerFor(properties.prop1) == handler1
        metadata.getAnnotationHandlerFor(properties.prop2) == handler2
        metadata.getAnnotationHandlerFor(properties.prop3) instanceof NoOpPropertyAnnotationHandler
    }

    def "annotation can be used for property annotation and injection annotations"() {
        def instantiationScheme = Stub(InstantiationScheme)
        instantiationScheme.injectionAnnotations >> [Thing2, InjectThing]
        def scheme = factory.inspectionScheme([Thing1, Thing2], instantiationScheme)

        expect:
        def metadata = scheme.metadataStore.getTypeMetadata(AnnotatedBean)
        def problems = []
        metadata.collectValidationFailures(null, new DefaultParameterValidationContext(problems))
        problems.empty
        metadata.propertiesMetadata.size() == 3

        def properties = metadata.propertiesMetadata.groupBy { it.propertyName }
        metadata.getAnnotationHandlerFor(properties.prop1) == handler1
        metadata.getAnnotationHandlerFor(properties.prop2) == handler2
        metadata.getAnnotationHandlerFor(properties.prop3) instanceof NoOpPropertyAnnotationHandler
    }

    def handler(Class<?> annotation) {
        def handler = Stub(PropertyAnnotationHandler)
        _ * handler.annotationType >> annotation
        return handler
    }
}

class AnnotatedBean {
    @Thing1
    String prop1

    @Thing2
    String prop2

    @InjectThing
    String prop3
}

@Retention(RetentionPolicy.RUNTIME)
@interface Thing1 {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Thing2 {
}

@Retention(RetentionPolicy.RUNTIME)
@interface InjectThing {
}
