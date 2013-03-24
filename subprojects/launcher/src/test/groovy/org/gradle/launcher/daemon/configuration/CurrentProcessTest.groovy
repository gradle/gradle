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

package org.gradle.launcher.daemon.configuration

import org.gradle.api.internal.file.FileResolver
import org.gradle.process.internal.JvmOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification
import org.gradle.initialization.BuildLayoutParameters

public class CurrentProcessTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    @Rule final SetSystemProperties systemPropertiesSet = new SetSystemProperties()
    private FileResolver fileResolver = Mock()
    private def currentJavaHome = tmpDir.file('java_home')
    private JvmOptions currentJvmOptions = new JvmOptions(fileResolver)
    private DaemonParameters parameters = new DaemonParameters(new BuildLayoutParameters())

    def "can only run build with identical java home"() {
        when:
        CurrentProcess currentProcess = new CurrentProcess(currentJavaHome, currentJvmOptions)

        then:
        currentProcess.configureForBuild(buildParameters(currentJavaHome))
        !currentProcess.configureForBuild(buildParameters(tmpDir.file('other')))
    }

    def "can only run build when no immutable jvm arguments specified"() {
        when:
        currentJvmOptions.setAllJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"])
        CurrentProcess currentProcess = new CurrentProcess(currentJavaHome, currentJvmOptions)

        then:
        currentProcess.configureForBuild(buildParameters([]))
        currentProcess.configureForBuild(buildParameters(['-Dfoo=bar']))

        and:
        !currentProcess.configureForBuild(buildParameters(["-Xms10m"]))
        !currentProcess.configureForBuild(buildParameters(["-XX:SomethingElse"]))
        !currentProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"]))
        !currentProcess.configureForBuild(buildParameters(['-Dfile.encoding=UTF8']))
    }

    def "sets all mutable system properties before running build"() {
        when:
        CurrentProcess currentProcess = new CurrentProcess(tmpDir.file('java_home'), currentJvmOptions)

        then:
        currentProcess.configureForBuild(buildParameters(["-Dfoo=bar", "-Dbaz"]))

        and:
        System.getProperty('foo') == 'bar'
        System.getProperty('baz') != null
    }

    private DaemonParameters buildParameters(Iterable<String> jvmArgs) {
        return buildParameters(currentJavaHome, jvmArgs)
    }

    private DaemonParameters buildParameters(File javaHome, Iterable<String> jvmArgs = []) {
        parameters.setJavaHome(javaHome)
        parameters.setJvmArgs(jvmArgs)
        return parameters
    }
}
