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

import org.gradle.api.JavaVersion
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
public class BuildProcessTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    @Rule
    final SetSystemProperties systemPropertiesSet = new SetSystemProperties()

    private FileResolver fileResolver = Mock()
    private def currentJvm = Stub(JavaInfo)
    private JvmOptions currentJvmOptions = new JvmOptions(fileResolver)

    def "can only run build with identical java home"() {
        when:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)

        then:
        buildProcess.configureForBuild(buildParameters(currentJvm))
        !buildProcess.configureForBuild(buildParameters(Stub(JavaInfo)))
    }

    def "all immutable jvm arguments or all immutable system properties need to match"() {
        when:
        def notDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) } find { it != Charset.defaultCharset() }
        currentJvmOptions.setAllJvmArgs(["-Dfile.encoding=$notDefaultEncoding", "-Xmx100m", "-XX:SomethingElse"])
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)

        then:
        buildProcess.configureForBuild(buildParameters([]))
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding"])) //only properties match
        !buildProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse"])) //only jvm argument match
        buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding", "-Xmx100m", "-XX:SomethingElse"])) //both match
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=${Charset.defaultCharset().name()}", "-Xmx100m", "-XX:SomethingElse"]))
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding", "-Xmx120m", "-XX:SomethingElse"]))
        buildProcess.configureForBuild(buildParameters(["-Dfoo=bar"]))
    }

    def "debug is handled as immutable argument"() {
        when:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)
        def debugEnabled = buildParameters([])
        debugEnabled.setDebug(true)
        def debugDisabled = buildParameters([])
        debugDisabled.setDebug(false)

        then:
        !buildProcess.configureForBuild(debugEnabled)
        buildProcess.configureForBuild(debugDisabled)
    }

    def "immutable system properties passed into the daemon parameter constructor are handled"() {
        when:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)
        def notDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) } find { it != Charset.defaultCharset() }

        then:
        buildProcess.configureForBuild(buildParameters([], [ "file.encoding" : Charset.defaultCharset().name() ]))
        !buildProcess.configureForBuild(buildParameters([], [ "file.encoding" : notDefaultEncoding.toString() ]))
    }

    def "can only run build when no immutable jvm arguments specified that do not match the current immutable jvm arguments"() {
        when:
        currentJvmOptions.setAllJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"])
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)


        then:
        buildProcess.configureForBuild(buildParameters([]))
        buildProcess.configureForBuild(buildParameters(['-Dfoo=bar']))

        and:
        !buildProcess.configureForBuild(buildParameters(["-Xms10m"]))
        !buildProcess.configureForBuild(buildParameters(["-XX:SomethingElse"]))
        buildProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"]))
        buildProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse"]))
        def notDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) } find { it != Charset.defaultCharset() }
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding"]))
        def notDefaultLanguage = ["es", "jp"].find { it != Locale.default.language }
        !buildProcess.configureForBuild(buildParameters(["-Duser.language=$notDefaultLanguage"]))
        buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=${Charset.defaultCharset().name()}"]))
        buildProcess.configureForBuild(buildParameters(["-Duser.language=${Locale.default.language}"]))
        !buildProcess.configureForBuild(buildParameters(["-Dcom.sun.management.jmxremote"]))
        !buildProcess.configureForBuild(buildParameters(["-Djava.io.tmpdir=/some/custom/folder"]))
    }

    def "sets all mutable system properties before running build"() {
        when:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)
        def parameters = buildParameters(["-Dfoo=bar", "-Dbaz"])

        then:
        buildProcess.configureForBuild(parameters)

        and:
        System.getProperty('foo') == 'bar'
        System.getProperty('baz') != null
    }

    def "defaults in required vm args are ignored"() {
        //if the user does not configure any jvm args Gradle uses some defaults
        //however, we don't want those defaults to influence the decision whether to use existing process or not
        given:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)

        when:
        def parametersWithDefaults = buildParameters()
        parametersWithDefaults.applyDefaultsFor(JavaVersion.current())

        then:
        buildProcess.configureForBuild(parametersWithDefaults)
    }

    def "user-defined vm args that correspond to defaults are not ignored"() {
        given:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)

        when:
        def parametersWithDefaults = buildParameters(DaemonParameters.DEFAULT_JVM_ARGS)

        then:
        !buildProcess.configureForBuild(parametersWithDefaults)
    }

    def "current and requested build vm match if vm arguments match"() {
        given:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)

        when:
        currentJvmOptions.minHeapSize = "16m"
        currentJvmOptions.maxHeapSize = "256m"
        currentJvmOptions.jvmArgs = ["-XX:+HeapDumpOnOutOfMemoryError"]

        then:
        buildProcess.configureForBuild(buildParameters(["-Xms16m", "-Xmx256m", "-XX:+HeapDumpOnOutOfMemoryError"]))
    }

    def "current and requested build vm do not match if vm arguments differ"() {
        given:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)

        when:
        currentJvmOptions.minHeapSize = "16m"
        currentJvmOptions.maxHeapSize = "1024m"
        currentJvmOptions.jvmArgs = ["-XX:+HeapDumpOnOutOfMemoryError"]

        then:
        !buildProcess.configureForBuild(buildParameters(["-Xms16m", "-Xmx256m", "-XX:+HeapDumpOnOutOfMemoryError"]))
    }

    def "current and requested build vm match if no arguments were set for requested vm"() {
        given:
        BuildProcess buildProcess = new BuildProcess(currentJvm, currentJvmOptions)

        when:
        currentJvmOptions.minHeapSize = "16m"
        currentJvmOptions.maxHeapSize = "1024m"
        currentJvmOptions.jvmArgs = ["-XX:+HeapDumpOnOutOfMemoryError"]

        then:
        buildProcess.configureForBuild(buildParameters())
    }

    private DaemonParameters buildParameters(Iterable<String> jvmArgs = []) {
        return buildParameters(currentJvm, jvmArgs)
    }

    private DaemonParameters buildParameters(Iterable<String> jvmArgs, Map<String, String> extraSystemProperties) {
        return buildParameters(currentJvm, jvmArgs, extraSystemProperties)
    }

    private static DaemonParameters buildParameters(JavaInfo jvm, Iterable<String> jvmArgs = [], Map<String, String> extraSystemProperties = Collections.emptyMap()) {
        def parameters = new DaemonParameters(new BuildLayoutParameters(), extraSystemProperties)
        parameters.setJvm(jvm)
        if (jvmArgs.iterator().hasNext()) {
            parameters.setJvmArgs(jvmArgs)
        }
        return parameters
    }
}
