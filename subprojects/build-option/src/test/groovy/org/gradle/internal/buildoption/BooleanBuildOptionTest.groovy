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

class BooleanBuildOptionTest extends Specification {

    public static final String DISABLED_LONG_OPTION = "no-$LONG_OPTION"
    def testSettings = new TestSettings()
    def commandLineParser = new CommandLineParser()

    def "can apply from property"() {
        given:
        def testOption = new TestOption(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION))

        when:
        testOption.applyFromProperty([:], testSettings)

        then:
        !testSettings.value
        !testSettings.origin

        when:
        testOption.applyFromProperty([(GRADLE_PROPERTY): 'true'], testSettings)

        then:
        testSettings.value
        testSettings.origin == BuildOption.Origin.GRADLE_PROPERTY
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
        CommandLineOption enabledOption = commandLineParser.optionsByString[LONG_OPTION]
        CommandLineOption disabledOption = commandLineParser.optionsByString[DISABLED_LONG_OPTION]
        assertNoArguments(enabledOption)
        assertNoArguments(disabledOption)
        assertNoDeprecationWarning(enabledOption)
        assertNoDeprecationWarning(disabledOption)
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
        assertIncubating(commandLineParser.optionsByString[LONG_OPTION], incubating)
        assertIncubating(commandLineParser.optionsByString[DISABLED_LONG_OPTION], incubating)

        where:
        incubating << [false, true]
    }

    def "can configure deprecated command line option"() {
        given:
        String deprecationWarning = 'replaced by other'

        when:
        def commandLineOptionConfiguration = CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION)
            .deprecated(deprecationWarning)

        def testOption = new TestOption(GRADLE_PROPERTY, commandLineOptionConfiguration)
        testOption.configure(commandLineParser)

        then:
        assertDeprecationWarning(commandLineParser.optionsByString[LONG_OPTION], deprecationWarning)
        assertDeprecationWarning(commandLineParser.optionsByString[DISABLED_LONG_OPTION], deprecationWarning)
    }

    def "can apply from command line"() {
        when:
        def testOption = new TestOption(GRADLE_PROPERTY)
        def options = [] as List<CommandLineOption>
        def parsedCommandLine = new ParsedCommandLine(options)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        !testSettings.value
        !testSettings.origin

        when:
        testOption = new TestOption(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION))
        def enabledOption = new CommandLineOption([LONG_OPTION])
        def disabledOption = new CommandLineOption([DISABLED_LONG_OPTION])
        options << enabledOption
        options << disabledOption
        parsedCommandLine = new ParsedCommandLine(options)
        parsedCommandLine.addOption(LONG_OPTION, enabledOption)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        testSettings.value
        testSettings.origin == BuildOption.Origin.COMMAND_LINE

        when:
        parsedCommandLine.addOption(DISABLED_LONG_OPTION, disabledOption)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        !testSettings.value
        testSettings.origin == BuildOption.Origin.COMMAND_LINE
    }

    static class TestOption extends BooleanBuildOption<TestSettings> {

        TestOption(String gradleProperty) {
            super(gradleProperty)
        }

        TestOption(String gradleProperty, CommandLineOptionConfiguration commandLineOptionConfiguration) {
            super(gradleProperty, commandLineOptionConfiguration)
        }

        @Override
        void applyTo(boolean value, TestSettings settings, BuildOption.Origin origin) {
            settings.value = value
            settings.origin = origin
        }
    }

    static class TestSettings {
        boolean value
        BuildOption.Origin origin
    }
}

