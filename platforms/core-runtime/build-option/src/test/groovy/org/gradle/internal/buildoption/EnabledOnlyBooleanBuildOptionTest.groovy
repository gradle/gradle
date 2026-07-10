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

package org.gradle.internal.buildoption

import org.gradle.cli.CommandLineOption
import org.gradle.cli.CommandLineParser
import org.gradle.cli.ParsedCommandLine
import spock.lang.Specification

import static org.gradle.internal.buildoption.BuildOptionFixture.*

class EnabledOnlyBooleanBuildOptionTest extends Specification {

    def testSettings = new TestSettings()
    def commandLineParser = new CommandLineParser()

    def "can apply from property"() {
        given:
        def testOption = new TestOption(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION))

        when:
        testOption.applyFromProperty([:], testSettings)

        then:
        !testSettings.flag
        !testSettings.origin

        when:
        testOption.applyFromProperty([(GRADLE_PROPERTY): 'val'], testSettings)

        then:
        testSettings.flag
        testSettings.origin instanceof Origin.GradlePropertyOrigin
        testSettings.origin.source == GRADLE_PROPERTY
    }

    def "can configure command line parser"() {
        when:
        def testOption = new TestOption(GRADLE_PROPERTY)
        testOption.configure(commandLineParser)

        then:
        !commandLineParser.optionsByString.containsKey(LONG_OPTION)
        !commandLineParser.optionsByString.containsKey(SHORT_OPTION)

        when:
        testOption = new TestOption(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION))
        testOption.configure(commandLineParser)

        then:
        CommandLineOption longOption = commandLineParser.optionsByString[LONG_OPTION]
        CommandLineOption shortOption = commandLineParser.optionsByString[SHORT_OPTION]
        assertNoArguments(longOption)
        assertNoArguments(shortOption)
        assertNotDeprecated(longOption)
        assertNotDeprecated(shortOption)
        assertDescription(longOption)
        assertDescription(shortOption)
    }

    def "can configure incubating command line option"() {
        when:
        def commandLineOptionConfiguration = CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION)

        if (incubating) {
            commandLineOptionConfiguration.incubating()
        }

        def testOption = new TestOption(GRADLE_PROPERTY, commandLineOptionConfiguration)
        testOption.configure(commandLineParser)

        then:
        CommandLineOption longOption = commandLineParser.optionsByString[LONG_OPTION]
        CommandLineOption shortOption = commandLineParser.optionsByString[SHORT_OPTION]
        assertIncubating(longOption, incubating)
        assertIncubating(shortOption, incubating)
        assertIncubatingDescription(longOption, incubating)
        assertIncubatingDescription(shortOption, incubating)

        where:
        incubating << [false, true]
    }

    def "can configure deprecated command line option"() {
        when:
        def commandLineOptionConfiguration = CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION)
            .deprecated()

        def testOption = new TestOption(GRADLE_PROPERTY, commandLineOptionConfiguration)
        testOption.configure(commandLineParser)

        then:
        CommandLineOption longOption = commandLineParser.optionsByString[LONG_OPTION]
        CommandLineOption shortOption = commandLineParser.optionsByString[SHORT_OPTION]
        assertDeprecated(longOption)
        assertDeprecated(shortOption)
        assertDeprecatedDescription(longOption, true)
        assertDeprecatedDescription(shortOption, true)
    }

    def "can apply from command line"() {
        when:
        def testOption = new TestOption(GRADLE_PROPERTY)
        def options = [] as List<CommandLineOption>
        def parsedCommandLine = new ParsedCommandLine(options)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        !testSettings.flag
        !testSettings.origin

        when:
        testOption = new TestOption(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION))
        def option = new CommandLineOption([LONG_OPTION])
        options << option
        parsedCommandLine = new ParsedCommandLine(options)
        parsedCommandLine.addOption(LONG_OPTION, option)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        testSettings.flag
        testSettings.origin instanceof Origin.CommandLineOrigin
        testSettings.origin.source == LONG_OPTION
    }

    static class TestOption extends EnabledOnlyBooleanBuildOption<TestSettings> {

        TestOption(String gradleProperty) {
            super(gradleProperty)
        }

        TestOption(String gradleProperty, CommandLineOptionConfiguration commandLineOptionConfiguration) {
            super(gradleProperty, commandLineOptionConfiguration)
        }

        @Override
        void applyTo(TestSettings settings, Origin origin) {
            settings.flag = true
            settings.origin = origin
        }
    }

    static class TestSettings {
        boolean flag
        Origin origin
    }
}
