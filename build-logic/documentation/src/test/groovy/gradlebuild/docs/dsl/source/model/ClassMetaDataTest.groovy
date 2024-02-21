/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs.dsl.source.model

import spock.lang.Specification

class ClassMetaDataTest extends Specification {
    def "is deprecated when @Deprecated annotation is attached to class"() {
        def notDeprecated = new ClassMetaData("SomeClass")
        def deprecated = new ClassMetaData("SomeClass")
        deprecated.addAnnotationTypeName(Deprecated.class.name)

        expect:
        !notDeprecated.deprecated
        deprecated.deprecated
    }

    def "is incubating when @Incubating annotation is attached to class"() {
        def notIncubating = new ClassMetaData("SomeClass")
        def incubating = new ClassMetaData("SomeClass")
        incubating.addAnnotationTypeName("org.gradle.api.Incubating")

        expect:
        !notIncubating.incubating
        incubating.incubating
    }
}
