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

package org.gradle.jvm.toolchain.internal

import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.jvm.toolchain.JvmVendorSpec
import spock.lang.Specification

import java.util.function.Predicate

class DefaultJvmVendorSpecTest extends Specification {

    def "unknown does not match known vendor"() {
        given:
        def metadata = Mock(JvmInstallationMetadata) {
            getVendor() >> JvmVendor.fromString("some unknown")
        }

        when:
        def asPredicate = (Predicate<JvmInstallationMetadata>)JvmVendorSpec.ADOPTOPENJDK

        then:
        !asPredicate.test(metadata)
    }

    def "matches known vendors"() {
        given:
        def metadata = Mock(JvmInstallationMetadata) {
            getVendor() >> JvmVendor.fromString("bellsoft")
        }

        expect:
        assertMatches(JvmVendorSpec.BELLSOFT, metadata)
        assertDoesNotMatch(JvmVendorSpec.IBM, metadata)
    }

    def "matches by raw string"() {
        given:
        def metadata = Mock(JvmInstallationMetadata) {
            getVendor() >> JvmVendor.fromString("someCustomJdk")
        }

        expect:
        assertDoesNotMatch(JvmVendorSpec.IBM, metadata)
        assertDoesNotMatch(JvmVendorSpec.AMAZON, metadata)
        assertMatches(JvmVendorSpec.matching("customjdk"), metadata)
    }

    void assertMatches(JvmVendorSpec spec, actualToolchain) {
        assert ((Predicate<JavaToolchain>) spec).test(actualToolchain)
    }

    void assertDoesNotMatch(JvmVendorSpec spec, actualToolchain) {
        assert !((Predicate<JavaToolchain>) spec).test(actualToolchain)
    }
}
