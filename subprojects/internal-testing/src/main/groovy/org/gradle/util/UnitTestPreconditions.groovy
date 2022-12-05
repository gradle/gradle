/*
 * Copyright 2022 the original author or authors.
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

import groovy.transform.CompileStatic
import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem
import org.testcontainers.DockerClientFactory;

@CompileStatic
class UnitTestPreconditions {

    static final TestPrecondition TRUE_REQUIREMENT = new TestPrecondition(
        () -> true
    )

    static final TestPrecondition FALSE_REQUIREMENT = new TestPrecondition(
        () -> false
    )

    static final TestPrecondition SYMLINKS_AVAILABLE = new TestPrecondition(
        () -> MAC_OS_X.getAsBoolean() || LINUX.getAsBoolean()
    )

    static final TestPrecondition NO_SYMLINKS = new TestPrecondition(
        () -> !SYMLINKS_AVAILABLE.getAsBoolean()
    )

    static final TestPrecondition CASE_INSENSITIVE_FS = new TestPrecondition(
        () -> MAC_OS_X.getAsBoolean() || WINDOWS.getAsBoolean()
    )
    static final TestPrecondition FILE_PERMISSIONS = new TestPrecondition(
        () -> MAC_OS_X.getAsBoolean() || LINUX.getAsBoolean()
    )
    static final TestPrecondition NO_FILE_PERMISSIONS = new TestPrecondition(
        () -> !FILE_PERMISSIONS.getAsBoolean()
    )
    static final TestPrecondition WORKING_DIR = new TestPrecondition(
        () -> JavaVersion.current() < JavaVersion.VERSION_11
    )
    static final TestPrecondition NO_FILE_LOCK_ON_OPEN = new TestPrecondition(
        () -> MAC_OS_X.getAsBoolean() || LINUX.getAsBoolean()
    )
    static final TestPrecondition MANDATORY_FILE_LOCKING = new TestPrecondition(
        () -> OperatingSystem.current().windows)
    )
    static final TestPrecondition WINDOWS = new TestPrecondition(
        () -> OperatingSystem.current().windows
    )
    static final TestPrecondition NOT_WINDOWS = new TestPrecondition(
        () -> !OperatingSystem.current().windows
    )
    static final TestPrecondition MAC_OS_X = new TestPrecondition(
        () -> OperatingSystem.current().macOsX
    )
    static final TestPrecondition MAC_OS_X_M1 = new TestPrecondition(
        () -> OperatingSystem.current().macOsX && OperatingSystem.current().toString().contains("aarch64")
    )
    static final TestPrecondition NOT_MAC_OS_X_M1 = new TestPrecondition(
        () -> !MAC_OS_X_M1.getAsBoolean()
    )
    static final TestPrecondition NOT_MAC_OS_X = new TestPrecondition(
        () -> !OperatingSystem.current().macOsX
    )
    static final TestPrecondition LINUX = new TestPrecondition(
        () -> OperatingSystem.current().linux
    )
    static final TestPrecondition NOT_LINUX = new TestPrecondition(
        () -> !LINUX.getAsBoolean()
    )
    static final TestPrecondition UNIX = new TestPrecondition(
        () -> OperatingSystem.current().unix
    )
    static final TestPrecondition UNIX_DERIVATIVE = new TestPrecondition(
        () -> MAC_OS_X.getAsBoolean() || LINUX.getAsBoolean() || UNIX.getAsBoolean()
    )

    static final TestPrecondition HAS_DOCKER = new TestPrecondition(
        () -> {
            try {
                DockerClientFactory.instance();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    )


    // Runtime JDKs
    static final TestPrecondition JDK8_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_1_8
    )
    static final TestPrecondition JDK9_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_1_9
    )
    static final TestPrecondition JDK9_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_1_9
    )
    static final TestPrecondition JDK10_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_1_10
    )
    static final TestPrecondition JDK10_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_1_10
    )
    static final TestPrecondition JDK11_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_11
    )
    static final TestPrecondition JDK11_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_11
    )
    static final TestPrecondition JDK12_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_12
    )
    static final TestPrecondition JDK13_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_13
    )
    static final TestPrecondition JDK13_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_13
    )
    static final TestPrecondition JDK14_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_14
    )
    static final TestPrecondition JDK15_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_15
    )
    static final TestPrecondition JDK16_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_16
    )
    static final TestPrecondition JDK16_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_16
    )
    static final TestPrecondition JDK17_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_17
    )
    static final TestPrecondition JDK17_OR_EARLIER = new TestPrecondition(
        () -> JavaVersion.current() <= JavaVersion.VERSION_17
    )
    static final TestPrecondition JDK18_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_18
    )
    static final TestPrecondition JDK19_OR_LATER = new TestPrecondition(
        () -> JavaVersion.current() >= JavaVersion.VERSION_19
    )
    static final TestPrecondition JDK_ORACLE = new TestPrecondition(
        () -> System.getProperty('java.vm.vendor') == 'Oracle Corporation'
    )

    // Available JDKs
    static final TestPrecondition ONLINE = new TestPrecondition(() -> {
        try {
            new URL("http://google.com").openConnection().getInputStream().close()
            return true
        } catch (IOException) {
            return false
        }
    })
    static final TestPrecondition CAN_INSTALL_EXECUTABLE = new TestPrecondition(
        () -> FILE_PERMISSIONS.getAsBoolean() || WINDOWS.getAsBoolean()
    )
    static final TestPrecondition SMART_TERMINAL = new TestPrecondition(
        () -> System.getenv("TERM").toUpperCase() != "DUMB"
    )
    static final TestPrecondition XCODE = new TestPrecondition(() ->
        // Simplistic approach at detecting Xcode by assuming macOS imply Xcode is present
        MAC_OS_X.getAsBoolean()
    )
    static final TestPrecondition MSBUILD = new TestPrecondition(() ->
        // Simplistic approach at detecting MSBuild by assuming Windows imply MSBuild is present
        WINDOWS.getAsBoolean() && "embedded" != System.getProperty("org.gradle.integtest.executer")
    )

    static final TestPrecondition SUPPORTS_TARGETING_JAVA6 = new TestPrecondition(
        () -> !JDK12_OR_LATER.getAsBoolean()
    )
    // Currently mac agents are not that strong so we avoid running high-concurrency tests on them
    static final TestPrecondition HIGH_PERFORMANCE = new TestPrecondition(
        () -> NOT_MAC_OS_X.getAsBoolean()
    )
    static final TestPrecondition NOT_EC2_AGENT = new TestPrecondition(
        () -> !InetAddress.getLocalHost().getHostName().startsWith("ip-")
    )

    static final TestPrecondition STABLE_GROOVY = new TestPrecondition(
        () -> !GroovySystem.version.endsWith("-SNAPSHOT")
    )

    static final TestPrecondition NOT_STABLE_GROOVY = new TestPrecondition(
        () -> !STABLE_GROOVY.getAsBoolean()
    )

    static final TestPrecondition BASH_AVAILABLE = new TestPrecondition(
        () -> ExecutableLocator.locate("bash")
    )
    static final TestPrecondition DASH_AVAILABLE = new TestPrecondition(
        () -> ExecutableLocator.locate("dash")
    )
    static final TestPrecondition STATIC_SH_AVAILABLE = new TestPrecondition(
        () -> ExecutableLocator.locate("static-sh")
    )
    static final TestPrecondition SHELLCHECK_AVAILABLE = new TestPrecondition(
        () -> ExecutableLocator.locate("shellcheck")
    )

}
