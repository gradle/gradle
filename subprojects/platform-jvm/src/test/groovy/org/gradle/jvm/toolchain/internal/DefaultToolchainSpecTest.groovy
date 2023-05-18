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
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.util.Matchers
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultToolchainSpecTest extends Specification {

    def "spec key implements equals"() {
        given:
        def spec11 = new DefaultToolchainSpec(TestUtil.objectFactory())
        def spec12 = new DefaultToolchainSpec(TestUtil.objectFactory())
        def spec11Vendor1 = new DefaultToolchainSpec(TestUtil.objectFactory())
        def spec11Vendor2 = new DefaultToolchainSpec(TestUtil.objectFactory())
        def spec11Impl1 = new DefaultToolchainSpec(TestUtil.objectFactory())
        def spec11Impl2 = new DefaultToolchainSpec(TestUtil.objectFactory())

        when:
        spec11.languageVersion.set(JavaLanguageVersion.of(11))
        spec12.languageVersion.set(JavaLanguageVersion.of(12))
        spec11Vendor1.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Vendor1.vendor.set(JvmVendorSpec.AMAZON)
        spec11Vendor2.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Vendor2.vendor.set(JvmVendorSpec.matching("foo"))
        spec11Impl1.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Impl1.implementation.set(JvmImplementation.VENDOR_SPECIFIC)
        spec11Impl2.languageVersion.set(JavaLanguageVersion.of(11))
        spec11Impl2.implementation.set(JvmImplementation.J9)

        spec12.languageVersion.set(JavaLanguageVersion.of(12))

        then:
        Matchers.strictlyEquals(spec11.toKey(), spec11.toKey())
        Matchers.strictlyEquals(spec11.toKey(), spec11Impl1.toKey())
        Matchers.strictlyEquals(spec11Vendor1.toKey(), spec11Vendor1.toKey())
        !Matchers.strictlyEquals(spec11Vendor1.toKey(), spec11Vendor2.toKey())
        !Matchers.strictlyEquals(spec11.toKey(), spec12.toKey())
        Matchers.strictlyEquals(spec11Impl1.toKey(), spec11Impl1.toKey())
        !Matchers.strictlyEquals(spec11Impl1.toKey(), spec11Impl2.toKey())
        !Matchers.strictlyEquals(spec11Vendor1.toKey(), spec11Vendor2.toKey())
    }
}
