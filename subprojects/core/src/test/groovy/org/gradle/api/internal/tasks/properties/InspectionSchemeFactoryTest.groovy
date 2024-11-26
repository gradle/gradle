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

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.InstantiationScheme
import org.gradle.internal.properties.annotations.PropertyAnnotationHandler
import org.gradle.internal.reflect.DefaultTypeValidationContext
import org.gradle.internal.reflect.annotations.impl.DefaultTypeAnnotationMetadataStore
import org.gradle.util.TestUtil
import spock.lang.Specification

import javax.inject.Inject
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

import static org.gradle.internal.reflect.annotations.AnnotationCategory.TYPE

class InspectionSchemeFactoryTest extends Specification {
    def handler1 = handler(Thing1)
    def handler2 = handler(Thing2)
    def cacheFactory = new TestCrossBuildInMemoryCacheFactory()
    def typeAnnotationMetadataStore = new DefaultTypeAnnotationMetadataStore(
        [],
        [(Thing1): TYPE, (Thing2): TYPE],
        [:],
        ["java", "groovy"],
        [],
        [Object, GroovyObject],
        [ConfigurableFileCollection, Property],
        [IgnoredThing],
        [],
        { false },
        cacheFactory
    )
    def factory = new InspectionSchemeFactory([], [handler1, handler2], [], typeAnnotationMetadataStore, cacheFactory)

    def "creates inspection scheme that understands given property annotations and injection annotations"() {
        def instantiationScheme = Stub(InstantiationScheme)
        instantiationScheme.injectionAnnotations >> [Inject]
        def scheme = factory.inspectionScheme([Thing1, Thing2], [], [], instantiationScheme)

        when:
        def metadata = scheme.metadataStore.getTypeMetadata(AnnotatedBean)

        then:
        metadata.propertiesMetadata.size() == 2

        when:
        def validationContext = DefaultTypeValidationContext.withoutRootType(false, TestUtil.problemsService())
        metadata.visitValidationFailures(null, validationContext)

        then:
        validationContext.problems.isEmpty()

        when:
        def properties = metadata.propertiesMetadata.groupBy { it.propertyName }

        then:
        metadata.getAnnotationHandlerFor(properties.prop1) == handler1
        metadata.getAnnotationHandlerFor(properties.prop2) == handler2
    }

    def "annotation can be used for property annotation and injection annotations"() {
        def instantiationScheme = Stub(InstantiationScheme)
        instantiationScheme.injectionAnnotations >> [Thing2, Inject]
        def scheme = factory.inspectionScheme([Thing1, Thing2], [], [], instantiationScheme)

        when:
        def metadata = scheme.metadataStore.getTypeMetadata(AnnotatedBean)

        then:
        metadata.propertiesMetadata.size() == 2

        when:
        def validationContext = DefaultTypeValidationContext.withoutRootType(false, TestUtil.problemsService())
        metadata.visitValidationFailures(null, validationContext)

        then:
        validationContext.problems.isEmpty()

        when:
        def properties = metadata.propertiesMetadata.groupBy { it.propertyName }

        then:
        metadata.getAnnotationHandlerFor(properties.prop1) == handler1
        metadata.getAnnotationHandlerFor(properties.prop2) == handler2
    }

    def handler(Class<?> annotation) {
        def handler = Stub(PropertyAnnotationHandler)
        _ * handler.propertyRelevant >> true
        _ * handler.annotationType >> annotation
        return handler
    }
}

class AnnotatedBean {
    @Thing1
    String prop1

    @Thing2
    String prop2

    @Inject
    String prop3
}

@Retention(RetentionPolicy.RUNTIME)
@interface Thing1 {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Thing2 {
}

@Retention(RetentionPolicy.RUNTIME)
@interface IgnoredThing {
}
