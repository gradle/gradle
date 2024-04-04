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

package org.gradle.interal.buildconfiguration.tasks

import groovy.test.NotYetImplemented
import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults
import org.gradle.internal.buildconfiguration.fixture.BuildPropertiesFixture
import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

class UpdateDaemonJvmIntegrationTest extends AbstractIntegrationSpec implements BuildPropertiesFixture {

    def "root project has an updateDaemonJvm task only"() {
        buildFile << """
            def updateDaemonJvm = tasks.named("updateDaemonJvm").get()
            assert updateDaemonJvm instanceof ${UpdateDaemonJvm.class.name}
            assert updateDaemonJvm.description == "Generates or updates the Daemon JVM criteria."
        """
        settingsFile << """
            rootProject.name = 'root'
            include("sub")
        """
        file("sub").mkdir()

        expect:
        succeeds("help", "--task", "updateDaemonJvm")
        fails(":sub:updateDaemonJvm") // should not exist
    }

    def "When execute updateDaemonJvm without options Then build properties are populated with default values"() {
        when:
        run "updateDaemonJvm"

        then:
        assertJvmCriteria(Jvm.current().javaVersion)
    }

    def "When execute updateDaemonJvm for valid version Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-version=${version.majorVersion}"

        then:
        assertJvmCriteria(version)

        where:
        version << [JavaVersion.VERSION_1_8, JavaVersion.VERSION_15, JavaVersion.VERSION_HIGHER]
    }

    def "When execute updateDaemonJvm for valid vendor option Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-vendor=$vendor"

        then:
        assertJvmCriteria(BuildPropertiesDefaults.TOOLCHAIN_VERSION, vendor)

        where:
        vendor << ["ADOPTIUM", "ADOPTOPENJDK", "AMAZON", "APPLE", "AZUL", "BELLSOFT", "GRAAL_VM", "HEWLETT_PACKARD", "IBM", "JETBRAINS", "MICROSOFT", "ORACLE", "SAP", "TENCENT", "UNKNOWN"]
    }

    def "When execute updateDaemonJvm for valid implementation option Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-implementation=$implementation"

        then:
        assertJvmCriteria(BuildPropertiesDefaults.TOOLCHAIN_VERSION, null, implementation)

        where:
        implementation << ["VENDOR_SPECIFIC", "J9"]
    }

    def "When execute updateDaemonJvm specifying different options Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-version=17", "--toolchain-vendor=IBM", "--toolchain-implementation=J9"

        then:
        assertJvmCriteria(JavaVersion.VERSION_17, "IBM", "J9")
    }

    def "When execute updateDaemonJvm specifying different options in lower case Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-version=17", "--toolchain-vendor=ibm", "--toolchain-implementation=j9"

        then:
        assertJvmCriteria(JavaVersion.VERSION_17, "IBM", "J9")
    }

    @NotYetImplemented
    def "When execute updateDaemonJvm with invalid argument --toolchain-version option Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm", "--toolchain-version=$invalidVersion"

        then:
        failureDescriptionContains("Execution failed for task ':updateDaemonJvm'.")
        failureHasCause("Invalid integer value $invalidVersion provided for the 'toolchain-version' option. The supported values are in the range [8, $JavaVersion.VERSION_HIGHER.majorVersion].")

        where:
        invalidVersion << ["0", "-10", "7", "10000"]
    }

    @NotYetImplemented
    def "When execute updateDaemonJvm with invalid format --toolchain-version option Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm", "--toolchain-version=asdf"

        then:
        failureDescriptionContains("Problem configuring option 'toolchain-version' on task ':updateDaemonJvm' from command line.")
        failureHasCause("Could not determine Java version from 'asdf'")
    }

    def "When execute updateDaemonJvm with unexpected --toolchain-vendor option Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm", "--toolchain-vendor=unknown-vendor"

        then:
        failureDescriptionContains("Problem configuring option 'toolchain-vendor' on task ':updateDaemonJvm' from command line.")
        failureHasCause("Cannot convert string value 'unknown-vendor' to an enum value of type 'org.gradle.internal.jvm.inspection.JvmVendor\$KnownJvmVendor' " +
            "(valid case insensitive values: ADOPTIUM, ADOPTOPENJDK, AMAZON, APPLE, AZUL, BELLSOFT, GRAAL_VM, HEWLETT_PACKARD, IBM, JETBRAINS, MICROSOFT, ORACLE, SAP, TENCENT, UNKNOWN)")
    }

    def "When execute updateDaemonJvm with unexpected --toolchain-implementation option Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm", "--toolchain-implementation=unknown-implementation"

        then:
        failureDescriptionContains("Problem configuring option 'toolchain-implementation' on task ':updateDaemonJvm' from command line.")
        failureHasCause("Cannot convert string value 'unknown-implementation' to an enum value of type 'org.gradle.jvm.toolchain.JvmImplementation' " +
            "(valid case insensitive values: VENDOR_SPECIFIC, J9)")
    }

    def "Given already existing build properties When execute updateDaemonJvm with different criteria Then criteria get modified and the other build properties are stripped"() {
        given:
        file("gradle/gradle-build.properties") << """
            test.property=testValue
            daemon.jvm.toolchain.version=17
            daemon.jvm.toolchain.vendor=IBM
            daemon.jvm.toolchain.implementation=J9
            another.property=anotherValue
            # comments are stripped
        """

        when:
        run "updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL"

        then:
        assertJvmCriteria(JavaVersion.VERSION_20, "AZUL")
        def buildProperties = buildPropertiesFile.properties
        !buildProperties.containsKey("test.property")
        !buildProperties.containsKey("another.property")
        !buildPropertiesFile.text.contains("# comments are stripped")
        buildPropertiesFile.text.contains("#This file is generated by updateDaemonJvm")
    }

    def "Given defined invalid criteria When execute updateDaemonJvm with different criteria Then criteria get modified using java home"() {
        def currentJvm = JavaVersion.current()

        given:
        writeJvmCriteria(currentJvm, "invalidVendor")

        expect:
        succeeds("updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL")
        assertJvmCriteria(JavaVersion.VERSION_20, "AZUL")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined valid criteria matching with local toolchain When execute updateDaemonJvm with different criteria Then criteria get modified using the expected local toolchain"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        writeJvmCriteria(otherJvm.javaVersion, otherMetadata.vendor.knownVendor.name())

        expect:
        succeeds("updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL")
        assertJvmCriteria(JavaVersion.VERSION_20, "AZUL")
    }
}
