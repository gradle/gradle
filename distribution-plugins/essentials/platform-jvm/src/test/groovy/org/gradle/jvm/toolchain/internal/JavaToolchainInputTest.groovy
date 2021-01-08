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
import org.gradle.util.TestUtil
import spock.lang.Specification

class JavaToolchainInputTest extends Specification {

    def "optional properties are using defaults"() {
        given:
        def baseSpec = new DefaultToolchainSpec(TestUtil.objectFactory())
        def diffSpec = new DefaultToolchainSpec(TestUtil.objectFactory())
        baseSpec.languageVersion.set(JavaLanguageVersion.of(11))
        diffSpec.languageVersion.set(JavaLanguageVersion.of(11))
        def base = new JavaToolchainInput(baseSpec)
        def diff = new JavaToolchainInput(diffSpec)

        expect:
        base.languageVersion == diff.languageVersion
        base.vendor == diff.vendor
        base.implementation == diff.implementation
    }

    def "language version is stable"() {
        given:
        def base = new JavaToolchainInput(newSpec(11))
        def diff = new JavaToolchainInput(newSpec(11))

        expect:
        base.languageVersion == diff.languageVersion
        base.vendor == diff.vendor
        base.implementation == diff.implementation
    }

    def "change in language version is a different input"() {
        given:
        def base = new JavaToolchainInput(newSpec(11))
        def diff = new JavaToolchainInput(newSpec(14))

        expect:
        base.languageVersion != diff.languageVersion
        base.vendor == diff.vendor
        base.implementation == diff.implementation
    }

    def "change in vendor is a different input"() {
        given:
        def base = new JavaToolchainInput(newSpec(11, "adoptopenjdk"))
        def diff = new JavaToolchainInput(newSpec(11, "amazon"))

        expect:
        base.languageVersion == diff.languageVersion
        base.vendor != diff.vendor
        base.implementation == diff.implementation
    }

    def "change in implementation is a different input"() {
        given:
        def base = new JavaToolchainInput(newSpec(11, "adoptopenjdk", JvmImplementation.VENDOR_SPECIFIC))
        def diff = new JavaToolchainInput(newSpec(11, "adoptopenjdk", JvmImplementation.J9))

        expect:
        base.languageVersion == diff.languageVersion
        base.vendor == diff.vendor
        base.implementation != diff.implementation
    }

    def newSpec(int languageVersion, String vendor = "ibm", JvmImplementation impl = JvmImplementation.VENDOR_SPECIFIC) {
        def spec = new DefaultToolchainSpec(TestUtil.objectFactory())
        spec.languageVersion.set(JavaLanguageVersion.of(languageVersion))
        spec.vendor.set(JvmVendorSpec.matching(vendor))
        spec.implementation.set(impl)
        spec
    }
}
