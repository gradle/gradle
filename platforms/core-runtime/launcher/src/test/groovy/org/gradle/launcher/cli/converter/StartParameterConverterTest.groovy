/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.launcher.cli.converter

import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.cli.CommandLineParser
import org.gradle.initialization.layout.BuildLayoutFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class StartParameterConverterTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def userHome = tmpDir.file("user-home")

    def "copies build layout options to start parameter"() {
        def projectDir = new File("project-dir").absoluteFile

        expect:
        def parameters = convert("--project-dir", "project-dir")
        parameters.gradleUserHomeDir == userHome
        parameters.projectDir == projectDir
    }

    def "can provide logging option as command-line option"() {
        expect:
        def parameter = convert("-d")
        parameter.logLevel == LogLevel.DEBUG
    }

    def "can provide logging option as system property on command-line"() {
        expect:
        def parameter = convert("-Dorg.gradle.logging.level=DEBUG")
        parameter.logLevel == LogLevel.DEBUG
    }

    def "can provide logging option as persistent property"() {
        expect:
        userHome.file("gradle.properties") << "org.gradle.logging.level=DEBUG"
        def parameter = convert()
        parameter.logLevel == LogLevel.DEBUG
    }

    def "can provide parallelism option as command-line option"() {
        expect:
        def parameter = convert("--max-workers", "123")
        parameter.maxWorkerCount == 123
    }

    def "can provide parallelism option as system property on command-line"() {
        expect:
        def parameter = convert("-Dorg.gradle.workers.max=123")
        parameter.maxWorkerCount == 123
    }

    def "can provide parallelism option as persistent property"() {
        expect:
        userHome.file("gradle.properties") << "org.gradle.workers.max=123"
        def parameter = convert()
        parameter.maxWorkerCount == 123
    }

    def "can provide system property on command-line"() {
        expect:
        def parameter = convert("-Dsome.prop", "-Dother.prop=123")
        parameter.systemPropertiesArgs["some.prop"] == ""
        parameter.systemPropertiesArgs["other.prop"] == "123"
    }

    def "can provide project property on command-line"() {
        expect:
        def parameter = convert("-Psome.prop", "-Pother.prop=123")
        parameter.projectProperties == ["some.prop": "", "other.prop": "123"]
    }

    def "can provide start parameter option as command-line option"() {
        expect:
        def parameter = convert("--configuration-cache")
        parameter.getConfigurationCache().get()
    }

    def "can provide start parameter option as system property on command-line"() {
        expect:
        def parameter = convert("-Dorg.gradle.configuration-cache=true")
        parameter.getConfigurationCache().get()
    }

    def "can provide start parameter option as persistent property"() {
        expect:
        userHome.file("gradle.properties") << "org.gradle.configuration-cache=true"
        def parameter = convert()
        parameter.getConfigurationCache().get()
    }

    def "system property on command-line has precedence over persistent property"() {
        expect:
        userHome.file("gradle.properties") << "org.gradle.workers.max=123"
        def parameters1 = convert()
        def parameters2 = convert("-Dorg.gradle.workers.max=456")
        parameters1.maxWorkerCount == 123
        parameters2.maxWorkerCount == 456
    }

    def "command-line option has precedence over system property on command-line"() {
        expect:
        userHome.file("gradle.properties") << "org.gradle.workers.max=123"
        def parameters1 = convert("-Dorg.gradle.workers.max=456")
        def parameters2 = convert("--max-workers", "789", "-Dorg.gradle.workers.max=456")
        parameters1.maxWorkerCount == 456
        parameters2.maxWorkerCount == 789
    }

    def "agent mode is #agentMode and console is #console with args #args and env #env"() {
        when:
        def parameter = convert(env, args as String[])

        then:
        parameter.agentMode == agentMode
        parameter.consoleOutput == console

        where:
        args                                          | env                         | agentMode | console
        []                                            | [:]                         | false     | ConsoleOutput.Auto
        ["--agent"]                                   | [:]                         | true      | ConsoleOutput.Plain
        ["--no-agent"]                                | [:]                         | false     | ConsoleOutput.Auto
        ["-Dorg.gradle.agent=true"]                   | [:]                         | true      | ConsoleOutput.Plain
        []                                            | [ORG_GRADLE_AGENT: "true"]  | true      | ConsoleOutput.Plain
        ["-Dorg.gradle.agent=false"]                  | [ORG_GRADLE_AGENT: "true"]  | true      | ConsoleOutput.Plain
        ["-Dorg.gradle.agent=true"]                   | [ORG_GRADLE_AGENT: "false"] | false     | ConsoleOutput.Auto
        ["--no-agent"]                                | [ORG_GRADLE_AGENT: "true"]  | false     | ConsoleOutput.Auto
        ["--agent"]                                   | [ORG_GRADLE_AGENT: "false"] | true      | ConsoleOutput.Plain
        ["--no-agent", "-Dorg.gradle.agent=true"]     | [:]                         | false     | ConsoleOutput.Auto
        ["--agent", "--console=rich"]                 | [:]                         | true      | ConsoleOutput.Plain
        ["--agent", "--console=plain"]                | [:]                         | true      | ConsoleOutput.Plain
        ["--console=rich"]                            | [ORG_GRADLE_AGENT: "true"]  | true      | ConsoleOutput.Plain
        ["-Dorg.gradle.agent=true", "--console=rich"] | [:]                         | true      | ConsoleOutput.Plain
        ["--no-agent", "--console=rich"]              | [:]                         | false     | ConsoleOutput.Rich
    }

    def "records but does not apply agent mode for the Tooling API"() {
        when:
        def parameter = convert(StartParameterConverter.forToolingApi(), [ORG_GRADLE_AGENT: "true"], "--console=rich")

        then:
        parameter.agentMode
        parameter.consoleOutput == ConsoleOutput.Rich
    }

    def "can enable agent mode as persistent property"() {
        expect:
        userHome.file("gradle.properties") << "org.gradle.agent=true"
        convert().agentMode
        !convert("--no-agent").agentMode
        !convert("-Dorg.gradle.agent=false").agentMode
    }

    StartParameterInternal convert(String... args) {
        convert([:], args)
    }

    StartParameterInternal convert(Map<String, String> env, String... args) {
        convert(StartParameterConverter.forCommandLine(), env, args)
    }

    StartParameterInternal convert(StartParameterConverter converter, Map<String, String> env, String... args) {
        def initialPropertiesConverter = new InitialPropertiesConverter()
        def buildLayoutConverter = new BuildLayoutConverter()
        def propertiesConverter = new LayoutToPropertiesConverter(new BuildLayoutFactory())

        def parser = new CommandLineParser()
        initialPropertiesConverter.configure(parser)
        buildLayoutConverter.configure(parser)
        converter.configure(parser)
        def parsedCommandLine = parser.parse(args)
        def initialProperties = initialPropertiesConverter.convert(parsedCommandLine)
        def buildLayout = buildLayoutConverter.convert(initialProperties, parsedCommandLine, null) {
            it.gradleUserHomeDir = userHome // don't use the default
        }
        def properties = propertiesConverter.convert(initialProperties, buildLayout)

        return converter.convert(parsedCommandLine, buildLayout, properties, env, new StartParameterInternal())
    }
}
