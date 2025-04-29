/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.context


import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.launcher.daemon.configuration.DaemonPriority
import org.gradle.launcher.daemon.toolchain.DaemonJvmCriteria
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

class DaemonCompatibilitySpecSpec extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())

    private DaemonRequestContext request
    private DaemonContext candidate = Mock(DaemonContext)

    private TestFile javaHome = tmp.createDir("jdk")

    def setup() {
        javaHome.file("bin", OperatingSystem.current().getExecutableName("java")).touch()
    }

    DaemonRequestContext clientWants(Map args) {
        clientWants(args.jvmCriteria,
            args.daemonOpts ?: [],
            args.applyInstrumentationAgent ?: false,
            args.nativeServicesMode ?: NativeServices.NativeServicesMode.NOT_SET,
            args.priority?: DaemonPriority.NORMAL)
    }

    DaemonRequestContext clientWants(DaemonJvmCriteria jvmCriteria,
                                     Collection<String> daemonOpts = Collections.emptyList(),
                                     boolean applyInstrumentationAgent = false,
                                     NativeServices.NativeServicesMode nativeServicesMode = NativeServices.NativeServicesMode.NOT_SET,
                                     DaemonPriority priority = DaemonPriority.NORMAL) {
        request = new DaemonRequestContext(jvmCriteria, daemonOpts, applyInstrumentationAgent, nativeServicesMode, priority)
    }

    private boolean isCompatible() {
        new DaemonCompatibilitySpec(request).isSatisfiedBy(candidate)
    }

    private String getUnsatisfiedReason() {
        new DaemonCompatibilitySpec(request).whyUnsatisfied(candidate)
    }

    def "contexts with different java homes are incompatible"() {
        clientWants(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome))

        def daemonJdkHome = tmp.createDir("daemon-jdk")
        daemonJdkHome.file("bin", OperatingSystem.current().getExecutableName("java")).touch()

        candidate.javaHome >> daemonJdkHome

        expect:
        !compatible
        unsatisfiedReason.contains "JVM is incompatible"
    }

    def "contexts with different jvm criteria are incompatible"() {
        clientWants(new DaemonJvmCriteria.Spec(JavaLanguageVersion.of(11), JvmVendorSpec.ADOPTIUM, JvmImplementation.VENDOR_SPECIFIC, false))

        candidate.javaVersion >> JavaLanguageVersion.of(15)

        expect:
        !compatible
        unsatisfiedReason.contains "JVM is incompatible"
    }

    @Requires(UnitTestPreconditions.Symlinks)
    def "contexts with symlinked javaHome are compatible"() {
        def linkToJdk = tmp.testDirectory.file("link")
        linkToJdk.createLink(javaHome)

        assert javaHome != linkToJdk
        assert linkToJdk.exists()
        assert javaHome.canonicalFile == linkToJdk.canonicalFile

        clientWants(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome))

        candidate.javaHome >> linkToJdk
        candidate.daemonOpts >> []
        candidate.priority >> DaemonPriority.NORMAL
        candidate.shouldApplyInstrumentationAgent() >> false
        candidate.nativeServicesMode >> NativeServices.NativeServicesMode.NOT_SET

        expect:
        compatible

        cleanup:
        assert linkToJdk.delete()
    }

    def "contexts with same daemon opts are compatible"() {
        clientWants(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome), ["-Xmx256m", "-Dfoo=foo"])

        candidate.javaHome >> javaHome
        candidate.daemonOpts >> ["-Xmx256m", "-Dfoo=foo"]
        candidate.priority >> DaemonPriority.NORMAL
        candidate.shouldApplyInstrumentationAgent() >> false
        candidate.nativeServicesMode >> NativeServices.NativeServicesMode.NOT_SET

        expect:
        compatible
    }

    def "contexts with same daemon opts but different order are compatible"() {
        clientWants(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome), ["-Xmx256m", "-Dfoo=foo"])

        candidate.javaHome >> javaHome
        candidate.daemonOpts >> ["-Dfoo=foo", "-Xmx256m"]
        candidate.priority >> DaemonPriority.NORMAL
        candidate.shouldApplyInstrumentationAgent() >> false
        candidate.nativeServicesMode >> NativeServices.NativeServicesMode.NOT_SET

        expect:
        compatible
    }

    def "contexts with different quantity of opts are not compatible"() {
        clientWants(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome), ["-Xmx256m", "-Dfoo=foo"])
        candidate.javaHome >> javaHome
        candidate.daemonOpts >> ["-Xmx256m"]

        expect:
        !compatible
        unsatisfiedReason.contains "At least one daemon option is different"
    }

    def "contexts with different daemon opts are incompatible"() {
        clientWants(new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome), ["-Xmx256m", "-Dfoo=foo"])
        candidate.javaHome >> javaHome
        candidate.daemonOpts >> ["-Xmx256m", "-Dfoo=bar"]

        expect:
        !compatible
        unsatisfiedReason.contains "At least one daemon option is different"
    }

    def "contexts with different priority"() {
        clientWants(jvmCriteria: new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome), priority: DaemonPriority.LOW)
        candidate.javaHome >> javaHome
        candidate.daemonOpts >> []
        candidate.priority >> DaemonPriority.NORMAL

        expect:
        !compatible
        unsatisfiedReason.contains "Process priority is different"
    }

    def "context with different agent status"() {
        clientWants(jvmCriteria: new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome), applyInstrumentationAgent: clientStatus)
        candidate.javaHome >> javaHome
        candidate.daemonOpts >> []
        candidate.priority >> DaemonPriority.NORMAL
        candidate.shouldApplyInstrumentationAgent() >> !clientStatus

        expect:
        !compatible
        unsatisfiedReason.contains "Agent status is different"

        where:
        clientStatus << [true, false]
    }

    def "context with same agent status"() {
        clientWants(jvmCriteria: new DaemonJvmCriteria.JavaHome(DaemonJvmCriteria.JavaHome.Source.ORG_GRADLE_JAVA_HOME, javaHome), applyInstrumentationAgent: clientStatus)
        candidate.javaHome >> javaHome
        candidate.daemonOpts >> []
        candidate.priority >> DaemonPriority.NORMAL
        candidate.shouldApplyInstrumentationAgent() >> clientStatus
        candidate.nativeServicesMode >> NativeServices.NativeServicesMode.NOT_SET

        expect:
        compatible

        where:
        clientStatus << [true, false]
    }
}
