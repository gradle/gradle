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

class NoArgumentBuildOptionTest extends Specification {

    private static final String GRADLE_PROPERTY = 'org.gradle.test'
    private static final String LONG_OPTION = 'test'
    private static final String SHORT_OPTION = 't'
    private static final String DESCRIPTION = 'some test'

    def testSettings = new TestSettings()
    def commandLineParser = new CommandLineParser()

    def "can apply from property"() {
        given:
        def testOption = new TestOption(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION))

        when:
        testOption.applyFromProperty([:], testSettings)

        then:
        !testSettings.flag

        when:
        testOption.applyFromProperty([(GRADLE_PROPERTY): 'val'], testSettings)

        then:
        testSettings.flag
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
        assertNoArguments(commandLineParser.optionsByString[LONG_OPTION])
        assertNoArguments(commandLineParser.optionsByString[SHORT_OPTION])
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
        assertIncubating(commandLineParser.optionsByString[SHORT_OPTION], incubating)

        where:
        incubating << [false, true]
    }

    def "can apply from command line"() {
        when:
        def testOption = new TestOption(GRADLE_PROPERTY)
        def options = [] as List<CommandLineOption>
        def parsedCommandLine = new ParsedCommandLine(options)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        !testSettings.flag

        when:
        testOption = new TestOption(GRADLE_PROPERTY, CommandLineOptionConfiguration.create(LONG_OPTION, SHORT_OPTION, DESCRIPTION))
        def option = new CommandLineOption([LONG_OPTION])
        options << option
        parsedCommandLine = new ParsedCommandLine(options)
        parsedCommandLine.addOption(LONG_OPTION, option)
        testOption.applyFromCommandLine(parsedCommandLine, testSettings)

        then:
        testSettings.flag
    }

    static void assertNoArguments(CommandLineOption option) {
        assert !option.allowsArguments
        assert !option.allowsMultipleArguments
    }

    static void assertIncubating(CommandLineOption option, boolean incubating) {
        assert option.incubating == incubating
    }

    static class TestOption extends NoArgumentBuildOption<TestSettings> {

        TestOption(String gradleProperty) {
            super(gradleProperty)
        }

        TestOption(String gradleProperty, CommandLineOptionConfiguration commandLineOptionConfiguration) {
            super(gradleProperty, commandLineOptionConfiguration)
        }

        @Override
        void applyTo(TestSettings settings) {
            settings.flag = true
        }
    }

    static class TestSettings {
        boolean flag
    }
}
