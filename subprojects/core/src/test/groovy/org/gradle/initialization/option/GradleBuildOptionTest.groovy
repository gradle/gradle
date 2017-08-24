/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization.option

import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import spock.lang.Specification

class GradleBuildOptionTest extends Specification {

    private static final String MAX_WORKERS_GRADLE_PROPERTY = 'org.gradle.workers.max'
    private static final String MAX_WORKERS_COMMAND_LINE_OPTION = 'max-workers'
    private static final String MAX_WORKERS_DESCRIPTION = 'When configured, Gradle will use a maximum of the given number of workers.'
    def commandLineParser = new CommandLineParser()

    def "can create new option"() {
        given:
        def gradleProperty = 'my.prop'

        when:
        def stringOption = GradleBuildOption.createStringOption(gradleProperty)
        def booleanOption = GradleBuildOption.createBooleanOption(gradleProperty)

        then:
        stringOption.gradleProperty == gradleProperty
        booleanOption.gradleProperty == gradleProperty
        !stringOption.commandLineOption
        !booleanOption.commandLineOption
    }

    def "can create String-based option that doesn't take argument"() {
        given:
        def gradleProperty = MAX_WORKERS_GRADLE_PROPERTY
        def commandLineOption = MAX_WORKERS_COMMAND_LINE_OPTION

        when:
        def option = GradleBuildOption.createStringOption(gradleProperty)
        option.withCommandLineOption(commandLineOption, MAX_WORKERS_DESCRIPTION)

        then:
        option.commandLineOption.option == commandLineOption

        when:
        option.commandLineOption.registerOption(commandLineParser)

        then:
        parse("--${commandLineOption}").hasOption(commandLineOption)
    }

    def "can create String-based option that takes argument"() {
        given:
        def gradleProperty = MAX_WORKERS_GRADLE_PROPERTY
        def commandLineOption = MAX_WORKERS_COMMAND_LINE_OPTION

        when:
        def option = GradleBuildOption.createStringOption(gradleProperty)
        option.withCommandLineOption(commandLineOption, MAX_WORKERS_DESCRIPTION)
        option.commandLineOption.hasArgument()
        option.commandLineOption.registerOption(commandLineParser)

        then:
        parse("--${commandLineOption}=4").hasOption(commandLineOption)
    }

    def "can create Boolean-based option"() {
        given:
        def gradleProperty = 'org.gradle.daemon'
        def commandLineOption = 'daemon'
        def disabledCommandLineOption = 'no-daemon'

        when:
        def option = GradleBuildOption.createBooleanOption(gradleProperty)
        option.withCommandLineOption(commandLineOption, 'When set to true the Gradle daemon is used to run the build.')

        then:
        option.commandLineOption.enabledOption == commandLineOption
        option.commandLineOption.disabledOption == disabledCommandLineOption

        when:
        option.commandLineOption.registerOption(commandLineParser)

        then:
        parse("--${commandLineOption}").hasOption(commandLineOption)
        parse("--${disabledCommandLineOption}").hasOption(disabledCommandLineOption)

        when:
        def result = parse("--${commandLineOption}", "--${disabledCommandLineOption}")

        then:
        !result.hasOption(commandLineOption)
        result.hasOption(disabledCommandLineOption)
    }

    private ParsedCommandLine parse(String... commandLine) {
        commandLineParser.parse(commandLine)
    }
}
