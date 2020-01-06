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

import javax.tools.ToolProvider

enum TestPrecondition implements org.gradle.internal.Factory<Boolean> {
    NULL_REQUIREMENT({ true }),
    JNA({
        !UNKNOWN_OS.fulfilled
    }),
    NO_JNA({
        UNKNOWN_OS.fulfilled
    }),
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
    SET_ENV_VARIABLE({
        !UNKNOWN_OS.fulfilled
    }),
    WORKING_DIR({
        !UNKNOWN_OS.fulfilled && JavaVersion.current() < JavaVersion.VERSION_11
    }),
    PROCESS_ID({
        !UNKNOWN_OS.fulfilled
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
    UNKNOWN_OS({
        OperatingSystem.current().name == "unknown operating system"
    }),
    NOT_UNKNOWN_OS({
        !UNKNOWN_OS.fulfilled
    }),
    JDK7({
        JavaVersion.current() == JavaVersion.VERSION_1_7
    }),
    JDK7_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_1_7
    }),
    JDK9_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_1_9
    }),
    JDK8_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_1_8
    }),
    JDK8({
        JavaVersion.current() == JavaVersion.VERSION_1_8
    }),
    JDK8_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_1_8
    }),
    JDK9_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_1_9
    }),
    JDK10_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_1_10
    }),
    JDK11_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_11
    }),
    JDK12_OR_EARLIER({
        JavaVersion.current() <= JavaVersion.VERSION_12
    }),
    JDK12_OR_LATER({
        JavaVersion.current() >= JavaVersion.VERSION_12
    }),
    JDK7_POSIX({
        NOT_WINDOWS.fulfilled
    }),
    NOT_JDK_IBM({
        !JDK_IBM.fulfilled
    }),
    FIX_TO_WORK_ON_JAVA9({
        JDK8_OR_EARLIER.fulfilled
    }),
    JDK_IBM({
        System.getProperty('java.vm.vendor') == 'IBM Corporation'
    }),
    JDK_ORACLE({
        System.getProperty('java.vm.vendor') == 'Oracle Corporation'
    }),
    JDK({
        ToolProvider.systemJavaCompiler != null
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
    PULL_REQUEST_BUILD({
        if (System.getenv("TRAVIS")?.toUpperCase() == "TRUE") {
            return true
        }
        if (System.getenv("PULL_REQUEST_BUILD")?.toUpperCase() == "TRUE") {
            return true
        }
        return false
    }),
    NOT_PULL_REQUEST_BUILD({
        !PULL_REQUEST_BUILD.fulfilled
    }),
    KOTLIN_SCRIPT({
        JDK8_OR_LATER.fulfilled && NOT_JDK_IBM.fulfilled
    }),
    XCODE({
        // Simplistic approach at detecting Xcode by assuming macOS imply Xcode is present
        MAC_OS_X.fulfilled
    }),
    MSBUILD({
        // Simplistic approach at detecting MSBuild by assuming Windows imply MSBuild is present
        WINDOWS.fulfilled
    }),
    SUPPORTS_TARGETING_JAVA6({ !JDK12_OR_LATER.fulfilled }),
    // Currently mac agents are not that strong so we avoid running high-concurrency tests on them
    HIGH_PERFORMANCE(NOT_MAC_OS_X),
    NOT_EC2_AGENT({
        !InetAddress.getLocalHost().getHostName().startsWith("ip-")
    })

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

