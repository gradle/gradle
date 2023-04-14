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

import org.gradle.launcher.daemon.context.DaemonContext
import spock.lang.Specification

class DaemonContextParserTest extends Specification {
    def "parses entries without agent"() {
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
        DaemonContext parsedContext = DaemonContextParser.parseFromString(contextString)
        then:
        parsedContext != null
        !parsedContext.shouldApplyInstrumentationAgent()
    }

    def "parses entries with agent"() {
        def contextString = "DefaultDaemonContext[" +
            "uid=40b63fc1-2506-4fa8-bf48-1bfbfc6a457f," +
            "javaHome=/home/mlopatkin/.asdf/installs/java/temurin-11.0.16+101," +
            "daemonRegistryDir=/home/mlopatkin/gradle/local/.gradle/daemon," +
            "pid=1120028," +
            "idleTimeout=10800000," +
            "priority=NORMAL," +
            "applyInstrumentationAgent=$agentStatus," +
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
        DaemonContext parsedContext = DaemonContextParser.parseFromString(contextString)

        then:
        parsedContext != null
        parsedContext.shouldApplyInstrumentationAgent() == agentStatus

        where:
        agentStatus << [true, false]
    }
}
