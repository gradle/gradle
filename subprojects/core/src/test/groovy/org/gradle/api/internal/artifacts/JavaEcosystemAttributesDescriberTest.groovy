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

package org.gradle.api.internal.artifacts

import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.internal.attributes.AttributeContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.util.AttributeTestUtil.attributesFactory
import static org.gradle.util.AttributeTestUtil.named

class JavaEcosystemAttributesDescriberTest extends Specification {
    @Subject
    JavaEcosystemAttributesDescriber describer = new JavaEcosystemAttributesDescriber()

    AttributeContainerInternal attributes = attributesFactory().mutable()

    def "describes a library"() {
        when:
        attributes.attribute(Usage.USAGE_ATTRIBUTE, named(Usage, "java-api"))
            .attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "library"))
            .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements, "jar"))
            .attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)
            .attribute(ProjectInternal.STATUS_ATTRIBUTE, "release")

        then:
        describer.describeAttributeSet(attributes.asMap()) == "a library for use during compile-time, with a release status, compatible with Java 11, packaged as a jar"
    }

    def "describes a library with MAX_VALUE target JVM version"() {
        when:
        attributes.attribute(Usage.USAGE_ATTRIBUTE, named(Usage, "java-api"))
            .attribute(Category.CATEGORY_ATTRIBUTE, named(Category, "library"))
            .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements, "jar"))
            .attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.MAX_VALUE)
            .attribute(ProjectInternal.STATUS_ATTRIBUTE, "release")

        then:
        describer.describeAttributeSet(attributes.asMap()) == "a library for use during compile-time, with a release status, compatible with any Java version, packaged as a jar"
    }

    def "describes arbitrary attributes"() {
        when:
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, named(Category, Category.DOCUMENTATION))
            .attribute(DocsType.DOCS_TYPE_ATTRIBUTE, named(DocsType, DocsType.SAMPLES))
            .attribute(Bundling.BUNDLING_ATTRIBUTE, named(Bundling, Bundling.EMBEDDED))
            .attribute(Attribute.of('dummy', String), 'value')

        then:
        describer.describeAttributeSet(attributes.asMap()) == "samples, and its dependencies bundled (fat jar), as well as attribute 'dummy' with value 'value'"
    }

    def "uses the generic 'component' term when category isn't set"() {
        when:
        attributes.attribute(Usage.USAGE_ATTRIBUTE, named(Usage, "java-runtime"))
            .attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, named(LibraryElements, LibraryElements.CLASSES_AND_RESOURCES))
            .attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 11)

        then:
        describer.describeAttributeSet(attributes.asMap()) == "a component for use during runtime, compatible with Java 11, preferably not packaged as a jar"
    }

    def "describes incompatible documentation type even if category is missing"() {
        when:
        attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, named(DocsType, "something"))

        then:
        describer.describeAttributeSet(attributes.asMap()) == "documentation of type 'something'"
    }
}
