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
        parameter.systemPropertiesArgs == ["some.prop": "", "other.prop": "123"]
    }

    def "can provide project property on command-line"() {
        expect:
        def parameter = convert("-Psome.prop", "-Pother.prop=123")
        parameter.projectProperties == ["some.prop": "", "other.prop": "123"]
    }

    def "can provide start parameter option as command-line option"() {
        expect:
        def parameter = convert("--configuration-cache")
        parameter.configurationCache.get()
    }

    def "can provide start parameter option as system property on command-line"() {
        expect:
        def parameter = convert("-Dorg.gradle.configuration-cache=true")
        parameter.configurationCache.get()
    }

    def "can provide start parameter option as persistent property"() {
        expect:
        userHome.file("gradle.properties") << "org.gradle.configuration-cache=true"
        def parameter = convert()
        parameter.configurationCache.get()
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

    StartParameterInternal convert(String... args) {
        def converter = new StartParameterConverter()
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

        return converter.convert(parsedCommandLine, buildLayout, properties, new StartParameterInternal())
    }
}
