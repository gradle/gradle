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

package org.gradle.integtests.fixtures.daemon

import org.gradle.util.GradleVersion

class FakeDaemonLog {
    private final File log

    FakeDaemonLog(DaemonsFixture daemonsFixture) {
        this(daemonsFixture.daemonBaseDir, daemonsFixture.getVersion())
    }

    FakeDaemonLog(File daemonBaseDir, String gradleVersion) {
        def logDir = new File(daemonBaseDir, gradleVersion)
        logDir.mkdirs()
        log = new File(logDir, "daemon-fake.log")

        def version = GradleVersion.version(gradleVersion)
        if (version < GradleVersion.version("8.8")) {
            // Before 8.8
            log << "DefaultDaemonContext[uid=40b63fc1-2506-4fa8-bf48-1bfbfc6a457f,javaHome=/home/mlopatkin/.asdf/installs/java/temurin-11.0.16+101,daemonRegistryDir=/home/mlopatkin/gradle/local/.gradle/daemon,pid=1234,idleTimeout=1000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=NOT_SET,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,-Xms256m,-Duser.language=en,-Duser.variant]"
        } else if (version <= GradleVersion.version("8.9")) {
            // 8.8 - 8.9
            log << "DefaultDaemonContext[uid=40b63fc1-2506-4fa8-bf48-1bfbfc6a457f,javaHome=/home/mlopatkin/.asdf/installs/java/temurin-11.0.16+101,javaVersion=11,daemonRegistryDir=/home/mlopatkin/gradle/local/.gradle/daemon,pid=1234,idleTimeout=1000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=NOT_SET,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,-Xms256m,-Duser.language=en,-Duser.variant]"
        } else {
            // latest
            log << "DefaultDaemonContext[uid=40b63fc1-2506-4fa8-bf48-1bfbfc6a457f,javaHome=/home/mlopatkin/.asdf/installs/java/temurin-11.0.16+101,javaVersion=11,javaVendor=Oracle Corporation,daemonRegistryDir=/home/mlopatkin/gradle/local/.gradle/daemon,pid=1234,idleTimeout=1000,priority=NORMAL,applyInstrumentationAgent=true,nativeServicesMode=NOT_SET,daemonOpts=--add-opens=java.base/java.util=ALL-UNNAMED,-Xms256m,-Duser.language=en,-Duser.variant]"
        }
    }

    File getLogFile() {
        return log
    }

    void logException(String exceptionInDaemon) {
        log << exceptionInDaemon
        log << "\n"
    }
}
