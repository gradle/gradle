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
package org.gradle.util

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem
import org.testcontainers.DockerClientFactory

/**
 * Usage:
 * <pre>
 * <code>@</code>Requires(TestPrecondition.JDK17_OR_LATER)
 * def "test with environment expectations"() {
 *     // the test is executed with Java 17 or later
 * }
 * </pre>
 *
 * @see Requires
 */
enum TestPrecondition implements org.gradle.internal.Factory<Boolean> {
    NULL_REQUIREMENT({ true }),
    SYMLINKS({
        MAC_OS_X.fulfilled || LINUX.fulfilled
    }),
    NO_SYMLINKS({
        !SYMLINKS.fulfilled
    }),
    CASE_INSENSITIVE_FS({
        MAC_OS_X.fulfilled || WINDOWS.fulfilled
    }),
    FILE_PERMISSIONS({
        MAC_OS_X.fulfilled || LINUX.fulfilled
    }),
    NO_FILE_PERMISSIONS({
        !FILE_PERMISSIONS.fulfilled
    }),
    WORKING_DIR({
        JavaVersion.current() < JavaVersion.VERSION_11
    }),
    NO_FILE_LOCK_ON_OPEN({
        MAC_OS_X.fulfilled || LINUX.fulfilled
    }),
    MANDATORY_FILE_LOCKING({
        OperatingSystem.current().windows
    }),
    WINDOWS({
        OperatingSystem.current().windows
    }),
    NOT_WINDOWS({
        !OperatingSystem.current().windows
    }),
    MAC_OS_X({
        OperatingSystem.current().macOsX
    }),
    MAC_OS_X_M1({
        OperatingSystem.current().macOsX && OperatingSystem.current().toString().contains("aarch64")
    }),
    NOT_MAC_OS_X_M1({
        !MAC_OS_X_M1.fulfilled
    }),
    NOT_MAC_OS_X({
        !OperatingSystem.current().macOsX
    }),
    LINUX({
        OperatingSystem.current().linux
    }),
    NOT_LINUX({
        !LINUX.fulfilled
    }),
    UNIX({
        OperatingSystem.current().unix
    }),
    UNIX_DERIVATIVE({
        MAC_OS_X.fulfilled || LINUX.fulfilled || UNIX.fulfilled
    }),
    HAS_DOCKER({
        try {
            DockerClientFactory.instance().client()
        } catch (Exception ex) {
            return false
        }
        return true
    }),
    JDK8_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_1_8
    }),
    JDK9_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_1_9
    }),
    JDK9_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_1_9
    }),
    JDK10_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_1_10
    }),
    JDK10_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_1_10
    }),
    JDK11_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_11
    }),
    JDK11_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_11
    }),
    JDK12_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_12
    }),
    JDK13_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_13
    }),
    JDK13_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_13
    }),
    JDK14_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_14
    }),
    JDK15_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_15
    }),
    JDK16_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_16
    }),
    JDK16_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_16
    }),
    JDK17_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_17
    }),
    JDK17_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_17
    }),
    JDK18_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_18
    }),
    JDK19_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_19
    }),
    JDK20_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_20
    }),
    JDK_ORACLE({
        System.getProperty('java.vm.vendor') == 'Oracle Corporation'
    }),

    ONLINE({
        try {
            new URL("http://google.com").openConnection().getInputStream().close()
            true
        } catch (IOException) {
            false
        }
    }),
    CAN_INSTALL_EXECUTABLE({
        FILE_PERMISSIONS.fulfilled || WINDOWS.fulfilled
    }),
    SMART_TERMINAL({
        System.getenv("TERM")?.toUpperCase() != "DUMB"
    }),
    XCODE({
        // Simplistic approach at detecting Xcode by assuming macOS imply Xcode is present
        MAC_OS_X.fulfilled
    }),
    MSBUILD({
        // Simplistic approach at detecting MSBuild by assuming Windows imply MSBuild is present
        WINDOWS.fulfilled && "embedded" != System.getProperty("org.gradle.integtest.executer")
    }),
    SUPPORTS_TARGETING_JAVA6({ !JDK12_OR_LATER.fulfilled }),
    SUPPORTS_TARGETING_JAVA7({ !JDK20_OR_LATER.fulfilled }),
    // Currently mac agents are not that strong so we avoid running high-concurrency tests on them
    HIGH_PERFORMANCE(NOT_MAC_OS_X),
    NOT_EC2_AGENT({
        !InetAddress.getLocalHost().getHostName().startsWith("ip-")
    }),
    STABLE_GROOVY({ !GroovySystem.version.endsWith("-SNAPSHOT") }),
    NOT_STABLE_GROOVY({ !STABLE_GROOVY.fulfilled })

    /**
     * A predicate for testing whether the precondition is fulfilled.
     */
    private Closure predicate

    TestPrecondition(Closure predicate) {
        this.predicate = predicate
    }

    TestPrecondition(TestPrecondition aliasOf) {
        this.predicate = aliasOf.predicate
    }

    /**
     * Tells if the precondition is fulfilled.
     */
    boolean isFulfilled() {
        predicate()
    }

    @Override
    Boolean create() {
        return isFulfilled()
    }
}

