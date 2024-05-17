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
    public static final String DISABLED_DESCRIPTION = "Disables option --${LONG_OPTION}."
    def testSettings = new TestSettings()
    def commandLineParser = new CommandLineParser()

    def "can apply from property"() {
        given:
        def testOption = new TestOption(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION, DISABLED_DESCRIPTION))

        when:
        testOption.applyFromProperty([:], testSettings)

        then:
        !testSettings.value
        !testSettings.origin

        when:
        testOption.applyFromProperty([(GRADLE_PROPERTY): 'true'], testSettings)

        then:
        testSettings.value
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
        testOption = new TestOption(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION, DISABLED_DESCRIPTION))
        testOption.configure(commandLineParser)

        then:
        CommandLineOption enabledOption = commandLineParser.optionsByString[LONG_OPTION]
        CommandLineOption disabledOption = commandLineParser.optionsByString[DISABLED_LONG_OPTION]
        assertNoArguments(enabledOption)
        assertNoArguments(disabledOption)
        assertNotDeprecated(enabledOption)
        assertNotDeprecated(disabledOption)
        assertDescription(enabledOption, DESCRIPTION)
        assertDescription(disabledOption, DISABLED_DESCRIPTION)
    }

    def "can configure incubating command line option"() {
        when:
        def commandLineOptionConfiguration = BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION, DISABLED_DESCRIPTION)

        if (incubating) {
            commandLineOptionConfiguration.incubating()
        }

        def testOption = new TestOption(GRADLE_PROPERTY, commandLineOptionConfiguration)
        testOption.configure(commandLineParser)

        then:
        CommandLineOption enabledOption = commandLineParser.optionsByString[LONG_OPTION]
        CommandLineOption disabledOption = commandLineParser.optionsByString[DISABLED_LONG_OPTION]
        assertIncubating(enabledOption, incubating)
        assertIncubating(disabledOption, incubating)
        assertIncubatingDescription(enabledOption, incubating, DESCRIPTION)
        assertIncubatingDescription(disabledOption, incubating, DISABLED_DESCRIPTION)

        where:
        incubating << [false, true]
    }

    def "can configure deprecated command line option"() {
        when:
        def commandLineOptionConfiguration = BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION, DISABLED_DESCRIPTION)
            .deprecated()

        def testOption = new TestOption(GRADLE_PROPERTY, commandLineOptionConfiguration)
        testOption.configure(commandLineParser)

        then:
        CommandLineOption enabledOption = commandLineParser.optionsByString[LONG_OPTION]
        CommandLineOption disabledOption = commandLineParser.optionsByString[DISABLED_LONG_OPTION]
        assertDeprecated(enabledOption)
        assertDeprecated(disabledOption)
        assertDeprecatedDescription(enabledOption, true, DESCRIPTION)
        assertDeprecatedDescription(disabledOption, true, DISABLED_DESCRIPTION)
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
        testOption = new TestOption(GRADLE_PROPERTY, BooleanCommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION, DISABLED_DESCRIPTION))
        def enabledOption = new CommandLineOption([LONG_OPTION])
        def disabledOption = new CommandLineOption([DISABLED_LONG_OPTION])
        options << enabledOption
        options << disabledOption
        parsedCommandLine = new ParsedCommandLine(options)
        parsedCommandLine.addOption(LONG_OPTION, enabledOption)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        testSettings.value
        testSettings.origin instanceof Origin.CommandLineOrigin
        testSettings.origin.source == LONG_OPTION

        when:
        parsedCommandLine.addOption(DISABLED_LONG_OPTION, disabledOption)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        !testSettings.value
        testSettings.origin instanceof Origin.CommandLineOrigin
        testSettings.origin.source == DISABLED_LONG_OPTION
    }

    static class TestOption extends BooleanBuildOption<TestSettings> {

        TestOption(String gradleProperty) {
            super(gradleProperty)
        }

        TestOption(String gradleProperty, BooleanCommandLineOptionConfiguration commandLineOptionConfiguration) {
            super(gradleProperty, commandLineOptionConfiguration)
        }

        @Override
        void applyTo(boolean value, TestSettings settings, Origin origin) {
            settings.value = value
            settings.origin = origin
        }
    }

    static class TestSettings {
        boolean value
        Origin origin
    }
}

