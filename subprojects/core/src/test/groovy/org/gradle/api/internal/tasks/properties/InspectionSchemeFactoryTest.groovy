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


import org.gradle.api.internal.tasks.properties.annotations.PropertyAnnotationHandler
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import spock.lang.Specification

import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

class InspectionSchemeFactoryTest extends Specification {
    def handler1 = handler(Thing1)
    def handler2 = handler(Thing2)
    def factory = new InspectionSchemeFactory([handler1, handler2], [], new TestCrossBuildInMemoryCacheFactory())

    def "creates inspection scheme"() {
        def scheme = factory.inspectionScheme([Thing1, Thing2])

        expect:
        def metadata = scheme.metadataStore.getTypeMetadata(AnnotatedBean)
        def problems = []
        metadata.collectValidationFailures(null, new DefaultParameterValidationContext(problems))
        problems.empty
        metadata.propertiesMetadata.size() == 2
    }

    def "reuses inspection schemes"() {
        def scheme = factory.inspectionScheme([Thing1, Thing2])

        expect:
        factory.inspectionScheme([Thing2, Thing1]).is(scheme)
        factory.inspectionScheme([Thing2]) != scheme
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
}

@Retention(RetentionPolicy.RUNTIME)
@interface Thing1 {
}

@Retention(RetentionPolicy.RUNTIME)
@interface Thing2 {
}
