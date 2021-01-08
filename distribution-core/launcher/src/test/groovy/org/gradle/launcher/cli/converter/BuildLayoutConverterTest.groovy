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
import org.gradle.cli.CommandLineParser
import org.gradle.initialization.BuildLayoutParameters
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class BuildLayoutConverterTest extends Specification {
    @Rule
    SetSystemProperties systemProperties = new SetSystemProperties()

    def "can specify project directory using command-line argument"() {
        when:
        def dir = new File("some-dir").absoluteFile
        def parameters = convert(["--project-dir", "some-dir"])

        then:
        parameters.projectDir == dir
    }

    def "can specify Gradle user home directory using command-line argument"() {
        when:
        def dir = new File("some-dir").absoluteFile
        def parameters = convert(["--gradle-user-home", "some-dir"])

        then:
        parameters.gradleUserHomeDir == dir
    }

    def "can specify Gradle user home directory using -D system property command-line argument"() {
        when:
        def dir = new File("some-dir").absoluteFile
        def parameters = convert(["-Dgradle.user.home=some-dir"])

        then:
        parameters.gradleUserHomeDir == dir
    }

    def "can specify Gradle user home directory using system property of current JVM"() {
        when:
        def dir = new File("some-dir").absoluteFile
        System.setProperty("gradle.user.home", dir.absolutePath)
        def parameters = convert([])

        then:
        parameters.gradleUserHomeDir == dir
    }

    def "command-line argument wins over -D system property wins over current JVM system property"() {
        when:
        def dir1 = new File("dir1").absoluteFile
        def dir2 = new File("dir2").absoluteFile
        def dir3 = new File("dir3").absoluteFile

        System.setProperty("gradle.user.home", "dir1")

        def parameters1 = convert([])
        def parameters2 = convert(["-Dgradle.user.home=dir2"])
        def parameters3 = convert(["--gradle-user-home", "dir3", "-Dgradle.user.home=dir2"])

        then:
        parameters1.gradleUserHomeDir == dir1
        parameters2.gradleUserHomeDir == dir2
        parameters3.gradleUserHomeDir == dir3
    }

    def "caller can provide default layout properties"() {
        when:
        def dir = new File("dir").absoluteFile
        def parameters = convert(["--gradle-user-home", "dir"]) {
            gradleUserHomeDir = new File("ignore-me")
            projectDir = dir
        }

        then:
        parameters.gradleUserHomeDir == dir
        parameters.projectDir == dir
    }

    BuildLayoutParameters convert(List<String> args, @DelegatesTo(BuildLayoutParameters) Closure overrides = {}) {
        def result = toResult(args, overrides)

        def parameters = new BuildLayoutParameters()
        result.applyTo(parameters)

        assert result.gradleUserHomeDir == parameters.gradleUserHomeDir

        def startParameters = new StartParameterInternal()
        result.applyTo(startParameters)

        assert startParameters.gradleUserHomeDir == parameters.gradleUserHomeDir
        assert startParameters.currentDir == parameters.currentDir
        assert startParameters.projectDir == parameters.projectDir

        return parameters
    }

    BuildLayoutResult toResult(List<String> args, @DelegatesTo(BuildLayoutParameters) Closure overrides = {}) {
        def parser = new CommandLineParser()
        def initialPropertiesConverter = new InitialPropertiesConverter()
        def converter = new BuildLayoutConverter()
        initialPropertiesConverter.configure(parser)
        converter.configure(parser)
        def parsedCommandLine = parser.parse(args)
        return converter.convert(initialPropertiesConverter.convert(parsedCommandLine), parsedCommandLine, null) {
            overrides.delegate = it
            overrides()
        }
    }
}
