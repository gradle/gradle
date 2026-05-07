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

import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.util.Matchers
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion.*
import static org.gradle.jvm.toolchain.internal.DefaultToolchainSpecTest.SpecValidity.*

class DefaultToolchainSpecTest extends Specification {
    JavaToolchainSpec createSpec() {
        TestUtil.objectFactory().newInstance(DefaultToolchainSpec)
    }

    def "spec key implements equals"() {
        given:
        def spec11 = createSpec()
        def spec11Vendor1 = createSpec()
        def spec11Vendor2 = createSpec()
        def spec11Impl1 = createSpec()
        def spec11Impl2 = createSpec()
        def spec12 = createSpec()
        def spec21 = createSpec()
        def spec21Native = createSpec()

        when:
        spec11.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Vendor1.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Vendor1.vendor.set(JvmVendorSpec.AMAZON)
        spec11Vendor2.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Vendor2.vendor.set(JvmVendorSpec.matching("foo"))
        spec11Impl1.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Impl1.implementation.set(JvmImplementation.VENDOR_SPECIFIC)
        spec11Impl2.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Impl2.implementation.set(JvmImplementation.J9)

        spec12.languageVersion.set(JavaLanguageVersion.of(12))

        spec21.languageVersion.set(JavaLanguageVersion.of(21))
        spec21Native.languageVersion.set(JavaLanguageVersion.of(21))
        spec21Native.nativeImageCapable.set(true)

        then:
        Matchers.strictlyEquals(spec11.toKey(), spec11.toKey())
        Matchers.strictlyEquals(spec11.toKey(), spec11Impl1.toKey())
        Matchers.strictlyEquals(spec11Vendor1.toKey(), spec11Vendor1.toKey())
        !Matchers.strictlyEquals(spec11Vendor1.toKey(), spec11Vendor2.toKey())
        !Matchers.strictlyEquals(spec11.toKey(), spec12.toKey())
        Matchers.strictlyEquals(spec11Impl1.toKey(), spec11Impl1.toKey())
        !Matchers.strictlyEquals(spec11Impl1.toKey(), spec11Impl2.toKey())
        !Matchers.strictlyEquals(spec11Vendor1.toKey(), spec11Vendor2.toKey())
        Matchers.strictlyEquals(spec21Native.toKey(), spec21Native.toKey())
        !Matchers.strictlyEquals(spec21.toKey(), spec21Native.toKey())
    }

    def "spec is #validity when #reason"() {
        given:
        def spec = createSpec() as DefaultToolchainSpec

        when:
        if (vendor != null) {
            spec.vendor.set(vendor)
        }
        if (languageVersion != null) {
            spec.languageVersion.set(languageVersion)
        }

        then:
        spec.valid == validity.isValid

        where:
        reason                                          | languageVersion            | vendor                 | validity
        "language version is not set"                   | null                       | JvmVendorSpec.ADOPTIUM | INVALID
        "language version is set to UNKNOWN"            | UNKNOWN                    | JvmVendorSpec.ADOPTIUM | INVALID
        "language version is set"                       | JavaLanguageVersion.of(11) | JvmVendorSpec.ADOPTIUM | VALID
        "both language version and vendor are not set"  | null                       | null                   | VALID
    }

    enum SpecValidity {
        VALID, INVALID

        final boolean isValid

        SpecValidity() {
            this.isValid = this.name() == "VALID"
        }

        @Override
        String toString() {
            return this.name().toLowerCase()
        }
    }
}
