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
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.internal.jvm.JavaInfo
import org.gradle.process.internal.JvmOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

@UsesNativeServices
public class CurrentProcessTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    @Rule
    final SetSystemProperties systemPropertiesSet = new SetSystemProperties()

    private FileResolver fileResolver = Mock()
    private def currentJvm = Stub(JavaInfo)
    private JvmOptions currentJvmOptions = new JvmOptions(fileResolver)

    def "can only run build with identical java home"() {
        when:
        CurrentProcess currentProcess = new CurrentProcess(currentJvm, currentJvmOptions)

        then:
        currentProcess.configureForBuild(buildParameters(currentJvm))
        !currentProcess.configureForBuild(buildParameters(Stub(JavaInfo)))
    }

    def "can only run build when no immutable jvm arguments specified"() {
        when:
        currentJvmOptions.setAllJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"])
        CurrentProcess currentProcess = new CurrentProcess(currentJvm, currentJvmOptions)


        then:
        currentProcess.configureForBuild(buildParameters([]))
        currentProcess.configureForBuild(buildParameters(['-Dfoo=bar']))

        and:
        !currentProcess.configureForBuild(buildParameters(["-Xms10m"]))
        !currentProcess.configureForBuild(buildParameters(["-XX:SomethingElse"]))
        !currentProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"]))
        def notDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) } find { it != Charset.defaultCharset() }
        !currentProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding"]))
        def notDefaultLanguage = ["es", "jp"].find { it != Locale.default.language }
        !currentProcess.configureForBuild(buildParameters(["-Duser.language=$notDefaultLanguage"]))
        currentProcess.configureForBuild(buildParameters(["-Dfile.encoding=${Charset.defaultCharset().name()}"]))
        currentProcess.configureForBuild(buildParameters(["-Duser.language=${Locale.default.language}"]))
    }

    def "sets all mutable system properties before running build"() {
        when:
        CurrentProcess currentProcess = new CurrentProcess(currentJvm, currentJvmOptions)
        def parameters = buildParameters(["-Dfoo=bar", "-Dbaz"])

        then:
        currentProcess.configureForBuild(parameters)

        and:
        System.getProperty('foo') == 'bar'
        System.getProperty('baz') != null
    }

    def "when required opts contain an immutable default setting ignore it"() {
        //if the user does not configure any jvm args Gradle uses some defaults
        //however, we don't want those defaults to influence the decision whether to use existing process or not
        //e.g. those defaults should only be used for launching a new process
        //TODO SF this is a bit messy, let's try to clean this up
        when:
        CurrentProcess currentProcess = new CurrentProcess(currentJvm, currentJvmOptions)

        then:
        currentProcess.configureForBuild(buildParameters(["-Xmx1024m"]))
    }

    private DaemonParameters buildParameters(Iterable<String> jvmArgs) {
        return buildParameters(currentJvm, jvmArgs)
    }

    private static DaemonParameters buildParameters(JavaInfo jvm, Iterable<String> jvmArgs = []) {
        def parameters = new DaemonParameters(new BuildLayoutParameters())
        parameters.setJvm(jvm)
        parameters.setJvmArgs(jvmArgs)
        return parameters
    }
}
