/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.cli.converter

import org.gradle.api.internal.provider.DefaultPropertyFactory
import org.gradle.api.internal.provider.PropertyHost
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec
import org.gradle.launcher.daemon.configuration.DaemonJvmToolchainCriteriaOptions
import org.gradle.launcher.daemon.configuration.DaemonJvmToolchainSpec
import spock.lang.Specification

class PropertiesToDaemonJvmToolchainCriteriaConverterTest extends Specification {

    def converter = new DaemonJvmToolchainCriteriaOptions().propertiesConverter()
    def params = new DaemonJvmToolchainSpec(new DefaultPropertyFactory(Stub(PropertyHost)))

    def "configures from empty build properties"() {
        when:
        converter.convert([:], params)

        then:
        !params.getLanguageVersion().isPresent()
        params.getVendor().get() == DefaultJvmVendorSpec.any()
        params.getImplementation().get() == JvmImplementation.VENDOR_SPECIFIC
    }

    def "configures from build properties"() {
        when:
        converter.convert([
            (BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "17",
            (BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY): "IBM",
            (BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY): "J9",
        ], params)

        then:
        params.getLanguageVersion().get() == JavaLanguageVersion.of(17)
        params.getVendor().get() == JvmVendorSpec.IBM
        params.getImplementation().get() == JvmImplementation.J9
    }

    def "specifies valid version"() {
        when:
        converter.convert([(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "11"], params)

        then:
        params.getLanguageVersion().get() == JavaLanguageVersion.of(11)
        params.getVendor().get() == DefaultJvmVendorSpec.any()
        params.getImplementation().get() == JvmImplementation.VENDOR_SPECIFIC
    }

    def "shows nice message for non-number version"() {
        when:
        converter.convert([(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "any"], params)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Value 'any' given for daemon.jvm.toolchain.version Build property is invalid (the value should be an int)"
    }

    def "shows nice message for negative version"() {
        when:
        converter.convert([(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "-1"], params)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Value '-1' given for daemon.jvm.toolchain.version Build property is invalid (the value should be a positive int)"
    }

    def "shows nice message when specifying vendor without version"() {
        when:
        converter.convert([(BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY): "amazon"], params)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Option daemon.jvm.toolchain.version undefined on Build properties. Add it manually or execute 'updateDaemonJvm' task with desired criteria to fix it."
    }

    def "allows vendor with different formats"() {
        when:
        converter.convert([
            (BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY): vendor,
            (BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "11"
        ], params)

        then:
        params.getVendor().get() == JvmVendorSpec.HEWLETT_PACKARD

        where:
        vendor << ["HEWLETT_PACKARD", "hewlett_packard", "Hewlett_packard"]
    }

    def "shows nice message for invalid vendor"() {
        when:
        converter.convert([
            (BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY): vendor,
            (BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "17",
        ], params)

        then:
        def exception = thrown(RuntimeException)
        exception.message == "Option daemon.jvm.toolchain.vendor doesn't accept value '$vendor'. " +
            "Possible values are [ADOPTIUM, ADOPTOPENJDK, AMAZON, APPLE, AZUL, BELLSOFT, GRAAL_VM, HEWLETT_PACKARD, IBM, JETBRAINS, MICROSOFT, ORACLE, SAP, TENCENT, UNKNOWN]"

        where:
        vendor << ["HEWLETT PACKARD", "HEWLETT-PACKARD"]
    }

    def "shows nice message when specifying implementation without version"() {
        when:
        converter.convert([(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY): "J9"], params)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == "Option daemon.jvm.toolchain.version undefined on Build properties. Add it manually or execute 'updateDaemonJvm' task with desired criteria to fix it."
    }

    def "allows implementation with different formats"() {
        when:
        converter.convert([
            (BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY): implementation,
            (BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "11"
        ], params)

        then:
        params.getImplementation().get() == JvmImplementation.J9

        where:
        implementation << ["J9", "j9"]
    }

    def "shows nice message for invalid implementation"() {
        when:
        converter.convert([
            (BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY): implementation,
            (BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY): "17",
        ], params)

        then:
        def exception = thrown(RuntimeException)
        exception.message == "Option daemon.jvm.toolchain.implementation doesn't accept value '$implementation'. Possible values are [vendor-specific, J9]"

        where:
        implementation << ["J9 ", " J9", " J9 "]
    }
}
