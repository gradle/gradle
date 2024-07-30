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

package org.gradle.integtests.fixtures.daemon

import org.gradle.internal.nativeintegration.services.NativeServices
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.launcher.daemon.configuration.DaemonPriority
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.context.DefaultDaemonContext
import org.gradle.util.GradleVersion
import spock.lang.Specification

class DaemonContextParserTest extends Specification {
    def "parses entries before 8.8"() {
        def contextString = "DefaultDaemonContext[" +
            "uid=4224e700-9e7c-4964-ad43-d6950d561cc0," +
            "javaHome=/usr/bin/java/," +
            "daemonRegistryDir=/home/user/.gradle/daemon," +
            "pid=1115013," +
            "idleTimeout=10800000," +
            "priority=NORMAL," +
            "daemonOpts=" +
            (
                "--add-opens=java.base/java.util=ALL-UNNAMED," +
                    "--add-opens=java.base/java.lang=ALL-UNNAMED," +
                    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED," +
                    "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED," +
                    "--add-opens=java.base/java.nio.charset=ALL-UNNAMED," +
                    "--add-opens=java.base/java.net=ALL-UNNAMED," +
                    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED," +
                    "-XX:MaxMetaspaceSize=256m," +
                    "-XX:+HeapDumpOnOutOfMemoryError," +
                    "-Xms256m,-Xmx512m," +
                    "-Dfile.encoding=UTF-8," +
                    "-Duser.country=US," +
                    "-Duser.language=en," +
                    "-Duser.variant"
            ) + "]"
        when:
        DaemonContext parsedContext = DaemonContextParser.parseFromString(contextString, GradleVersion.version("8.7"))
        then:
        parsedContext != null
        parsedContext.javaVersion == JavaLanguageVersion.of(8) // hardcoded in fixture
        parsedContext.javaVendor == "unknown" // hardcoded in fixture
    }

    def "parses entries for #version"() {
        def contextString = "DefaultDaemonContext[" +
            "uid=40b63fc1-2506-4fa8-bf48-1bfbfc6a457f," +
            "javaHome=/home/mlopatkin/.asdf/installs/java/temurin-11.0.16+101," +
            "javaVersion=11," +
            "daemonRegistryDir=/home/mlopatkin/gradle/local/.gradle/daemon," +
            "pid=1120028," +
            "idleTimeout=10800000," +
            "priority=NORMAL," +
            "applyInstrumentationAgent=true," +
            "daemonOpts=" +
            (
                "--add-opens=java.base/java.util=ALL-UNNAMED," +
                    "--add-opens=java.base/java.lang=ALL-UNNAMED," +
                    "--add-opens=java.base/java.lang.invoke=ALL-UNNAMED," +
                    "--add-opens=java.prefs/java.util.prefs=ALL-UNNAMED," +
                    "--add-opens=java.base/java.nio.charset=ALL-UNNAMED," +
                    "--add-opens=java.base/java.net=ALL-UNNAMED," +
                    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED," +
                    "-XX:MaxMetaspaceSize=256m," +
                    "-XX:+HeapDumpOnOutOfMemoryError," +
                    "-Xms256m," +
                    "-Xmx512m," +
                    "-Dfile.encoding=UTF-8," +
                    "-Duser.country=US," +
                    "-Duser.language=en," +
                    "-Duser.variant"
            ) + "]"
        when:
        DaemonContext parsedContext = DaemonContextParser.parseFromString(contextString, GradleVersion.version(version))

        then:
        parsedContext != null
        parsedContext.javaVersion == JavaLanguageVersion.of(11)
        parsedContext.javaVendor == "unknown" // hardcoded in fixture

        where:
        version << ["8.8", "8.9"]
    }

    def "parses entries"() {
        def context = new DefaultDaemonContext(
            "40b63fc1-2506-4fa8-bf48-1bfbfc6a457f",
            new File("/home/mlopatkin/.asdf/installs/java/temurin-11.0.16+101"),
            JavaLanguageVersion.of(11),
            "Oracle Corporation",
            new File("/home/mlopatkin/gradle/local/.gradle/daemon"),
            1234L,
            1000,
            Arrays.asList("--add-opens=java.base/java.util=ALL-UNNAMED", "-Xms256m", "-Duser.language=en", "-Duser.variant"),
            true,
            NativeServices.NativeServicesMode.NOT_SET,
            DaemonPriority.NORMAL)

        def contextString = context.toString()
        when:
        DaemonContext parsedContext = DaemonContextParser.parseFromString(contextString, GradleVersion.current())

        then:
        parsedContext != null
        // check round trip
        parsedContext.equals(context)
    }
}
