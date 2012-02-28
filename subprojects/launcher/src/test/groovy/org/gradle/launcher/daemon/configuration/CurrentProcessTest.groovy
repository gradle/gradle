/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.configuration;


import org.gradle.api.internal.file.FileResolver
import org.gradle.process.internal.JvmOptions
import org.gradle.util.TemporaryFolder
import org.junit.Rule
import spock.lang.Specification

public class CurrentProcessTest extends Specification {
    @Rule final TemporaryFolder tmpDir = new TemporaryFolder()
    private FileResolver fileResolver = Mock()
    private def currentJavaHome = tmpDir.file('java_home')
    private JvmOptions currentJvmOptions = new JvmOptions(fileResolver)
    private DaemonParameters parameters = new DaemonParameters()

    def "supports build with identical java home"() {
        when:
        CurrentProcess currentProcess = new CurrentProcess(currentJavaHome, currentJvmOptions)

        then:
        currentProcess.supportsBuildParameters(parametersWithJavaHome(currentJavaHome))
        !currentProcess.supportsBuildParameters(parametersWithJavaHome(tmpDir.file('other')))
    }

    def "supports build with no jvm arguments specified"() {
        when:
        currentJvmOptions.setAllJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"])
        CurrentProcess currentProcess = new CurrentProcess(tmpDir.file('java_home'), currentJvmOptions)

        then:
        currentProcess.supportsBuildParameters(parametersWithJavaHome(tmpDir.file('java_home')))
    }

    def "supports build when managed jvm args match required arguments exactly"() {
        when:
        currentJvmOptions.setAllJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"])
        CurrentProcess currentProcess = new CurrentProcess(tmpDir.file('java_home'), currentJvmOptions)

        then:
        currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Xmx100m"]))
        currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Xmx100m", "-Dfoo=bar", "-Dbaz"]))

        !currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Xms10m"]))
        !currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Xmx101m"]))

        // Perhaps these should match
        !currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Xmx100m", "-XX:SomethingElse"]))
        !currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"]))
    }

    def "supports build when current process has all required system properties"() {
        when:
        currentJvmOptions.setAllJvmArgs(["-Dfoo=bar", "-Dbaz"])
        CurrentProcess currentProcess = new CurrentProcess(tmpDir.file('java_home'), currentJvmOptions)

        then:
        currentProcess.supportsBuildParameters(parametersWithJvmArgs([]))
        currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Dfoo=bar"]))
        currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Dfoo=bar", "-Dbaz"]))

        !currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Dother"]))
        !currentProcess.supportsBuildParameters(parametersWithJvmArgs(["-Dfoo=bar", "-Dbaz", "-Dother"]))
    }

    private DaemonParameters parametersWithJavaHome(File javaHome) {
        parameters.setJavaHome(javaHome)
        return parameters
    }

    private DaemonParameters parametersWithJvmArgs(Iterable<String> args) {
        parametersWithJavaHome(currentJavaHome)
        parameters.setJvmArgs(args)
        return parameters
    }
}
