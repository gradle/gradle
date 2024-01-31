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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.daemon.DaemonToolchainIntegrationSpec
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults
import org.gradle.internal.buildconfiguration.fixture.DaemonJvmPropertiesFixture
import org.gradle.internal.jvm.Jvm
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotEmbeddedExecutor)
class UpdateDaemonJvmTaskIntegrationTest extends DaemonToolchainIntegrationSpec {

    final daemonJvmFixture = new DaemonJvmPropertiesFixture(testDirectory)

    def "When executing tasks Then the output contains updateDaemonJvm task"() {
        when:
        run "tasks"

        then:
        output.contains("updateDaemonJvm - Generates or updates the Daemon JVM criteria.")
    }

    def "When execute updateDaemonJvm without options Then build properties are populated with default values"() {
        when:
        run "updateDaemonJvm"

        then:
        daemonJvmFixture.assertJvmCriteria(JavaVersion.latestSupportedLTS.getMajorVersion().toInteger())
    }

    def "When execute updateDaemonJvm for valid version Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-version=$version"

        then:
        daemonJvmFixture.assertJvmCriteria(version)

        where:
        version << (JavaVersion.VERSION_1_8.majorVersion.toInteger()..JavaVersion.VERSION_HIGHER.majorVersion.toInteger())
    }

    def "When execute updateDaemonJvm for valid vendor option Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-vendor=$vendor"

        then:
        daemonJvmFixture.assertJvmCriteria(BuildPropertiesDefaults.TOOLCHAIN_VERSION, vendor)

        where:
        vendor << ["ADOPTIUM", "ADOPTOPENJDK", "AMAZON", "APPLE", "AZUL", "BELLSOFT", "GRAAL_VM", "HEWLETT_PACKARD", "IBM", "JETBRAINS", "MICROSOFT", "ORACLE", "SAP", "TENCENT", "UNKNOWN"]
    }

    def "When execute updateDaemonJvm for valid implementation option Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-implementation=$implementation"

        then:
        daemonJvmFixture.assertJvmCriteria(BuildPropertiesDefaults.TOOLCHAIN_VERSION, null, implementation)

        where:
        implementation << ["VENDOR_SPECIFIC", "J9"]
    }

    def "When execute updateDaemonJvm specifying different options Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-version=17", "--toolchain-vendor=IBM", "--toolchain-implementation=J9"

        then:
        daemonJvmFixture.assertJvmCriteria(17, "IBM", "J9")
    }

    def "When execute updateDaemonJvm specifying different options in lower case Then build properties are populated with expected values"() {
        when:
        run "updateDaemonJvm", "--toolchain-version=17", "--toolchain-vendor=ibm", "--toolchain-implementation=j9"

        then:
        daemonJvmFixture.assertJvmCriteria(17, "IBM", "J9")
    }

    def "When execute updateDaemonJvm with invalid argument --toolchain-version option Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm", "--toolchain-version=$invalidVersion"

        then:
        failureDescriptionContains("Execution failed for task ':updateDaemonJvm'.")
        failureHasCause("Invalid integer value $invalidVersion provided for the 'toolchain-version' option. The supported values are in the range [8, $JavaVersion.VERSION_HIGHER.majorVersion].")

        where:
        invalidVersion << ["0", "-10", "7", "10000"]
    }

    def "When execute updateDaemonJvm with invalid format --toolchain-version option Then fails with expected exception message"() {
        when:
        fails "updateDaemonJvm", "--toolchain-version=17.0"

        then:
        failureDescriptionContains("Problem configuring option 'toolchain-version' on task ':updateDaemonJvm' from command line.")
        failureHasCause("Cannot convert string value '17.0' to an integer.")
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

    def "Given already existing build properties When execute updateDaemonJvm with different criteria Then criteria get modified but the other build properties are still present"() {
        given:
        file("gradle/gradle-build.properties") << """
            test.property=testValue
            daemon.jvm.toolchain.version=17
            daemon.jvm.toolchain.vendor=IBM
            daemon.jvm.toolchain.implementation=J9
            another.property=anotherValue
        """

        when:
        run "updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL"

        then:
        daemonJvmFixture.assertJvmCriteria(20, "AZUL")
        daemonJvmFixture.assertBuildPropertyExist("test.property=testValue")
        daemonJvmFixture.assertBuildPropertyExist("another.property=anotherValue")
    }

    def "Given defined invalid criteria When execute updateDaemonJvm with different criteria Then criteria get modified using java home"() {
        def currentJvm = Jvm.current()

        given:
        createDaemonJvmToolchainCriteria("-1", "invalidVendor")

        expect:
        succeedsTaskWithDaemonJvm(currentJvm, false, "updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL")
    }

    @Requires(IntegTestPreconditions.JavaHomeWithDifferentVersionAvailable)
    def "Given defined valid criteria matching with local toolchain When execute updateDaemonJvm with different criteria Then criteria get modified using the expected local toolchain"() {
        def otherJvm = AvailableJavaHomes.differentVersion
        def otherMetadata = AvailableJavaHomes.getJvmInstallationMetadata(otherJvm)

        given:
        createDaemonJvmToolchainCriteria(otherMetadata.languageVersion.majorVersion, otherMetadata.vendor.knownVendor.name())

        expect:
        succeedsTaskWithDaemonJvm(otherJvm, true, "updateDaemonJvm", "--toolchain-version=20", "--toolchain-vendor=AZUL")
    }
}
