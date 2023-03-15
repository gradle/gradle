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
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.agents.AgentStatus
import org.gradle.internal.jvm.JavaInfo
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.process.internal.JvmOptions
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import java.nio.charset.Charset

class BuildProcessTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    @Rule
    final SetSystemProperties systemPropertiesSet = new SetSystemProperties()

    private def fileCollectionFactory = TestFiles.fileCollectionFactory(tmpDir.testDirectory)
    private def currentJvm = Stub(JavaInfo)
    private def agentStatus = Stub(AgentStatus) {
        isAgentInstrumentationEnabled() >> true
    }

    def "current and requested build vm match if vm arguments match"() {
        given:
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.minHeapSize = "16m"
        currentJvmOptions.maxHeapSize = "256m"
        currentJvmOptions.jvmArgs = ["-XX:+HeapDumpOnOutOfMemoryError"]

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        buildProcess.configureForBuild(buildParameters(["-Xms16m", "-Xmx256m", "-XX:+HeapDumpOnOutOfMemoryError"]))
    }

    def "current and requested build vm do not match if vm arguments differ"() {
        given:
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.minHeapSize = "16m"
        currentJvmOptions.maxHeapSize = "1024m"
        currentJvmOptions.jvmArgs = ["-XX:+HeapDumpOnOutOfMemoryError"]

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        !buildProcess.configureForBuild(buildParameters(["-Xms16m", "-Xmx256m", "-XX:+HeapDumpOnOutOfMemoryError"]))
    }

    def "current and requested build vm match if java home matches"() {
        when:
        def buildProcess = createBuildProcess()

        then:
        buildProcess.configureForBuild(buildParameters(currentJvm))
        !buildProcess.configureForBuild(buildParameters(Stub(JavaInfo)))
    }

    def "all requested immutable jvm arguments and all immutable system properties need to match"() {
        given:
        def notDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) } find { it != Charset.defaultCharset() }
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.setAllJvmArgs(["-Dfile.encoding=$notDefaultEncoding", "-Xmx100m", "-XX:SomethingElse"])

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding"])) //only properties match
        !buildProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse"])) //only jvm argument match
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=${Charset.defaultCharset().name()}", "-Xmx100m", "-XX:SomethingElse"])) //encoding does not match
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding", "-Xmx120m", "-XX:SomethingElse"])) //memory does not match
        buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding", "-Xmx100m", "-XX:SomethingElse"])) //both match
    }

    def "current and requested build vm match if no arguments are requested"() {
        given:
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.minHeapSize = "16m"
        currentJvmOptions.maxHeapSize = "1024m"
        currentJvmOptions.jvmArgs = ["-XX:+HeapDumpOnOutOfMemoryError"]
        def emptyRequest = buildParameters([])

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        buildProcess.configureForBuild(emptyRequest)
    }

    def "current VM does not match if it was started with the default client heap size"() {
        given:
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.maxHeapSize = "64m"
        def defaultRequest = buildParameters(null as Iterable)

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        !buildProcess.configureForBuild(defaultRequest)
    }

    def "current and requested build vm match if no arguments are requested even if the daemon defaults are applied"() {
        //if the user does not configure any jvm args Gradle uses some defaults
        //however, we don't want those defaults to influence the decision whether to use existing process or not
        given:
        def requestWithDefaults = buildParameters((Iterable) null)

        when:
        def buildProcess = createBuildProcess()

        then:
        requestWithDefaults.getEffectiveJvmArgs().containsAll(DaemonParameters.DEFAULT_JVM_ARGS)
        buildProcess.configureForBuild(requestWithDefaults)
    }

    def "current and requested build vm match if only mutable arguments are requested"() {
        given:
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.minHeapSize = "16m"
        currentJvmOptions.maxHeapSize = "1024m"
        currentJvmOptions.jvmArgs = ["-XX:+HeapDumpOnOutOfMemoryError"]
        def requestWithMutableArgument = buildParameters(["-Dfoo=bar"])

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        buildProcess.configureForBuild(requestWithMutableArgument)
    }

    def "current and requested build vm match if only mutable arguments vary"() {
        given:
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.setAllJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"])

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        !buildProcess.configureForBuild(buildParameters(["-Xms10m", "-Dfoo=bar", "-Dbaz"]))
        !buildProcess.configureForBuild(buildParameters(["-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"]))
        buildProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"]))
        buildProcess.configureForBuild(buildParameters(["-Xmx100m", "-XX:SomethingElse"]))
    }

    def "debug is an immutable argument"() {
        given:
        def debugEnabled = buildParameters([])
        debugEnabled.setDebug(true)
        def debugDisabled = buildParameters([])
        debugDisabled.setDebug(false)

        when:
        BuildProcess buildProcess = createBuildProcess()

        then:
        !buildProcess.configureForBuild(debugEnabled)
        buildProcess.configureForBuild(debugDisabled)
    }

    def "immutable system properties are treated as immutable"() {
        given:
        def notDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) } find { it != Charset.defaultCharset() }
        def notDefaultLanguage = ["es", "jp"].find { it != Locale.default.language }
        def currentJvmOptions = new JvmOptions(fileCollectionFactory)
        currentJvmOptions.setAllJvmArgs(["-Xmx100m", "-XX:SomethingElse", "-Dfoo=bar", "-Dbaz"])

        when:
        def buildProcess = createBuildProcess(currentJvmOptions)

        then:
        buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=${Charset.defaultCharset().name()}"]))
        buildProcess.configureForBuild(buildParameters(["-Duser.language=${Locale.default.language}"]))
        !buildProcess.configureForBuild(buildParameters(["-Dfile.encoding=$notDefaultEncoding"]))
        !buildProcess.configureForBuild(buildParameters(["-Duser.language=$notDefaultLanguage"]))
        !buildProcess.configureForBuild(buildParameters(["-Dcom.sun.management.jmxremote"]))
        !buildProcess.configureForBuild(buildParameters(["-Djava.io.tmpdir=/some/custom/folder"]))
    }

    def "immutable system properties passed into the daemon parameter constructor are handled"() {
        given:
        def notDefaultEncoding = ["UTF-8", "US-ASCII"].collect { Charset.forName(it) } find { it != Charset.defaultCharset() }

        when:
        BuildProcess buildProcess = createBuildProcess()

        then:
        buildProcess.configureForBuild(buildParameters([], ["file.encoding": Charset.defaultCharset().name()]))
        !buildProcess.configureForBuild(buildParameters([], ["file.encoding": notDefaultEncoding.toString()]))
    }

    def "sets all mutable system properties before running build"() {
        when:
        def parameters = buildParameters(["-Dfoo=bar", "-Dbaz"])

        then:
        createBuildProcess().configureForBuild(parameters)

        and:
        System.getProperty('foo') == 'bar'
        System.getProperty('baz') != null
    }

    def "user can explicitly disable default daemon args by setting jvm args to empty"() {
        given:
        def emptyBuildParameters = buildParameters([])

        when:
        createBuildProcess().configureForBuild(emptyBuildParameters)

        then:
        !emptyBuildParameters.getEffectiveJvmArgs().containsAll(DaemonParameters.DEFAULT_JVM_ARGS)
    }

    def "user-defined vm args that correspond to daemon default are considered during matching"() {
        given:
        def parametersWithDefaults = buildParameters(DaemonParameters.DEFAULT_JVM_ARGS)

        when:
        def buildProcess = createBuildProcess()

        then:
        !buildProcess.configureForBuild(parametersWithDefaults)
    }

    def "instrumentation agent status is considered during matching"() {
        given:
        def desiredParameters = buildParameters([])
        desiredParameters.setApplyInstrumentationAgent(desiredInstrumentationStatus)

        when:
        def currentAgentStatus = Stub(AgentStatus) {
            isAgentInstrumentationEnabled() >> agentApplied
        }
        BuildProcess buildProcess = createBuildProcess(currentAgentStatus)

        then:
        buildProcess.configureForBuild(desiredParameters) == expectProcessToBeUsable

        where:
        desiredInstrumentationStatus | agentApplied || expectProcessToBeUsable
        // process without the agent applied can be used if no agent instrumentation is requested
        false                        | false        || true
        // process with the agent applied can be used if no agent instrumentation is requested.
        // We expect the code to avoid using the agent in this case
        false                        | true         || true
        // process without the agent applied cannot be used if the agent instrumentation is requested
        true                         | false        || false
        // process with the agent applied can be used if the agent instrumentation is requested
        true                         | true         || true
    }

    private BuildProcess createBuildProcess(AgentStatus agentStatus) {
        return createBuildProcess(new JvmOptions(fileCollectionFactory), agentStatus)
    }

    private BuildProcess createBuildProcess(JvmOptions jvmOptions = new JvmOptions(fileCollectionFactory), AgentStatus agentStatus = this.agentStatus) {
        return new BuildProcess(currentJvm, jvmOptions, agentStatus)
    }

    private DaemonParameters buildParameters(Iterable<String> jvmArgs) {
        return buildParameters(currentJvm, jvmArgs)
    }

    private DaemonParameters buildParameters(Iterable<String> jvmArgs, Map<String, String> extraSystemProperties) {
        return buildParameters(currentJvm, jvmArgs, extraSystemProperties)
    }

    private DaemonParameters buildParameters(JavaInfo jvm, Iterable<String> jvmArgs = [], Map<String, String> extraSystemProperties = Collections.emptyMap()) {
        def buildLayoutResult = Stub(BuildLayoutResult) {
            getGradleUserHomeDir() >> tmpDir.file("user-home-dir")
        }
        def parameters = new DaemonParameters(buildLayoutResult, fileCollectionFactory, extraSystemProperties)
        parameters.setJvm(jvm)
        if (jvmArgs != null) {
            parameters.setJvmArgs(jvmArgs)
        }
        parameters.applyDefaultsFor(JavaVersion.VERSION_1_7)
        return parameters
    }
}
