/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.cli

import spock.lang.Issue
import spock.lang.Specification

class CommandLineParserTest extends Specification {
    private final CommandLineParser parser = new CommandLineParser()

    def parsesEmptyCommandLine() {
        parser.option('a')
        parser.option('long-value')

        expect:
        def result = parser.parse([])
        !result.hasOption('a')
        !result.hasOption('long-value')
        result.extraArguments == []
    }

    def parsesShortOption() {
        parser.option('a')
        parser.option('b')

        expect:
        def result = parser.parse(['-a'])
        result.hasOption('a')
        !result.hasOption('b')
    }

    def canUseDoubleDashesForShortOptions() {
        parser.option('a')

        expect:
        def result = parser.parse(['--a'])
        result.hasOption('a')
    }

    def parsesShortOptionWithArgument() {
        parser.option('a').hasArgument()

        expect:
        def result = parser.parse(['-a', 'arg'])
        result.hasOption('a')
        result.option('a').value == 'arg'
        result.option('a').values == ['arg']
    }

    def parsesShortOptionWithAttachedArgument() {
        parser.option('a').hasArgument()

        expect:
        def result = parser.parse(['-aarg'])
        result.hasOption('a')
        result.option('a').value == 'arg'
        result.option('a').values == ['arg']
    }

    def attachedArgumentTakesPrecedenceOverCombinedOption() {
        parser.option('a').hasArgument()
        parser.option('b')

        expect:
        def result = parser.parse(['-ab'])
        result.hasOption('a')
        result.option('a').value == 'b'
        !result.hasOption('b')
    }

    def parsesShortOptionWithEqualArgument() {
        parser.option('a').hasArgument()

        expect:
        def result = parser.parse(['-a=arg'])
        result.hasOption('a')
        result.option('a').value == 'arg'
        result.option('a').values == ['arg']
    }

    def parsesShortOptionWithEqualMultilineArgument() {
        parser.option('a').hasArgument()

        expect:
        def result = parser.parse(['-a=1\n2\n3'])
        result.hasOption('a')
        result.option('a').value == '1\n2\n3'
        result.option('a').values == ['1\n2\n3']
    }

    def parsesShortOptionWithEqualsCharacterInAttachedArgument() {
        parser.option('a').hasArgument()

        expect:
        def result = parser.parse(['-avalue=arg'])
        result.hasOption('a')
        result.option('a').value == 'value=arg'
        result.option('a').values == ['value=arg']
    }

    def parsesShortOptionWithDashCharacterInAttachedArgument() {
        parser.option('a').hasArgument()

        expect:
        def result = parser.parse(['-avalue-arg'])
        result.hasOption('a')
        result.option('a').value == 'value-arg'
        result.option('a').values == ['value-arg']
    }

    def parsesCombinedShortOptions() {
        parser.option('a')
        parser.option('b')

        expect:
        def result = parser.parse(['-ab'])
        result.hasOption('a')
        result.hasOption('b')
    }

    def parsesLongOption() {
        parser.option('long-option-a')
        parser.option('long-option-b')

        expect:
        def result = parser.parse(['--long-option-a'])
        result.hasOption('long-option-a')
        !result.hasOption('long-option-b')
    }

    def canUseSingleDashForLongOptions() {
        parser.option('long')
        parser.option('other').hasArgument()

        expect:
        def result = parser.parse(['-long', '-other', 'arg'])
        result.hasOption('long')
        result.hasOption('other')
        result.option('other').value == 'arg'
    }

    def parsesLongOptionWithArgument() {
        parser.option('long-option-a').hasArgument()
        parser.option('long-option-b')

        expect:
        def result = parser.parse(['--long-option-a', 'arg'])
        result.hasOption('long-option-a')
        result.option('long-option-a').value == 'arg'
        result.option('long-option-a').values == ['arg']
    }

    def parsesLongOptionWithEqualsArgument() {
        parser.option('long-option-a').hasArgument()

        expect:
        def result = parser.parse(['--long-option-a=arg'])
        result.hasOption('long-option-a')
        result.option('long-option-a').value == 'arg'
        result.option('long-option-a').values == ['arg']
    }

    def "parse fails for invalid option name #badOptionName"() {
        when:
        parser.option(badOptionName)

        then:
        def e = thrown(IllegalArgumentException)
        e != null

        where:
        badOptionName << ['weird\nmulti\nline\noption', '!@#$', 'with space', '=', '-']
    }

    def parsesMultipleOptions() {
        parser.option('a').hasArgument()
        parser.option('long-option')

        expect:
        def result = parser.parse(['--long-option', '-a', 'arg'])
        result.hasOption('long-option')
        result.hasOption('a')
        result.option('a').value == 'arg'
    }

    def parsesOptionWithMultipleAliases() {
        parser.option('a', 'b', 'long-option-a')

        expect:
        def longOptionResult = parser.parse(['--long-option-a'])
        longOptionResult.hasOption('a')
        longOptionResult.hasOption('b')
        longOptionResult.hasOption('long-option-a')
        longOptionResult.option('a') == longOptionResult.option('long-option-a')
        longOptionResult.option('a') == longOptionResult.option('b')

        def shortOptionResult = parser.parse(['-a'])
        shortOptionResult.hasOption('a')
        shortOptionResult.hasOption('b')
        shortOptionResult.hasOption('long-option-a')
    }

    def parsesCommandLineWhenOptionAppearsMultipleTimes() {
        parser.option('a', 'b', 'long-option-a')

        expect:
        def result = parser.parse(['--long-option-a', '-a', '-a', '-b'])
        result.hasOption('a')
        result.hasOption('b')
        result.hasOption('long-option-a')
    }

    def parsesOptionWithMultipleArguments() {
        parser.option('a', 'long').hasArguments()

        expect:
        def result = parser.parse(['-a', 'arg1', '--long', 'arg2', '-aarg3', '--long=arg4'])
        result.hasOption('a')
        result.hasOption('long')
        result.option('a').values == ['arg1', 'arg2', 'arg3', 'arg4']
    }

    def parsesHelpOption() {
        parser.option('h', '?', 'help')

        expect:
        def result = parser.parse(['-?'])
        result.hasOption('?')
    }

    def parsesCommandLineWithSubcommand() {
        parser.option('a')

        expect:
        def singleArgResult = parser.parse(['a'])
        singleArgResult.extraArguments == ['a']
        !singleArgResult.hasOption('a')

        def multipleArgsResult = parser.parse(['a', 'b'])
        multipleArgsResult.extraArguments == ['a', 'b']
        !multipleArgsResult.hasOption('a')
    }

    def parsesCommandLineWithOptionsAndSubcommand() {
        parser.option('a')

        expect:
        def optionBeforeSubcommandResult = parser.parse(['-a', 'a'])
        optionBeforeSubcommandResult.extraArguments == ['a']
        optionBeforeSubcommandResult.hasOption('a')

        def optionAfterSubcommandResult = parser.parse(['a', '-a'])
        optionAfterSubcommandResult.extraArguments == ['a', '-a']
        !optionAfterSubcommandResult.hasOption('a')
    }

    def parsesCommandLineWithOptionsAndSubcommandWhenMixedOptionsAllowed() {
        parser.option('a')
        parser.allowMixedSubcommandsAndOptions()

        expect:
        def optionBeforeSubcommandResult = parser.parse(['-a', 'a'])
        optionBeforeSubcommandResult.extraArguments == ['a']
        optionBeforeSubcommandResult.hasOption('a')

        def optionAfterSubcommandResult = parser.parse(['a', '-a'])
        optionAfterSubcommandResult.extraArguments == ['a']
        optionAfterSubcommandResult.hasOption('a')
    }

    def parsesCommandLineWithSubcommandThatHasOptions() {
        when:
        def result = parser.parse(['a', '--option', 'b'])

        then:
        result.extraArguments == ['a', '--option', 'b']

        when:
        parser.allowMixedSubcommandsAndOptions()
        result = parser.parse(['a', '--option', 'b'])

        then:
        result.extraArguments == ['a', '--option', 'b']
    }

    def canCombineSubcommandShortOptionWithOtherShortOptions() {
        parser.option('b')
        parser.allowMixedSubcommandsAndOptions()

        when:
        def result = parser.parse(['cmd', '-b', '-a'])

        then:
        result.extraArguments == ['cmd', '-a']
        result.hasOption('b')

        when:
        result = parser.parse(['cmd', '-ba'])

        then:
        result.extraArguments == ['cmd', '-a']
        result.hasOption('b')
    }

    def returnsLastMutuallyExclusiveOptionThatIsPresent() {
        parser.option("a")
        parser.option("b")
        parser.option("c", "long-option")
        parser.option("d")
        parser.allowOneOf("a", "b", "c")

        when:
        def result = parser.parse(['-a', '-b', '-c'])

        then:
        !result.hasOption('a')
        !result.hasOption('b')
        result.hasOption('c')
        result.hasOption('long-option')

        when:
        result = parser.parse(['-a', '-b', '--long-option'])

        then:
        !result.hasOption('a')
        !result.hasOption('b')
        result.hasOption('c')
        result.hasOption('long-option')
    }

    def singleDashIsNotConsideredAnOption() {
        expect:
        def result = parser.parse(['-'])
        result.extraArguments == ['-']
    }

    def doubleDashMarksEndOfOptions() {
        parser.option('a')

        expect:
        def result = parser.parse(['--', '-a'])
        result.extraArguments == ['-a']
        !result.hasOption('a')
    }

    def valuesEmptyWhenOptionIsNotPresentInCommandLine() {
        parser.option('a').hasArgument()

        expect:
        def result = parser.parse([])
        result.option('a').values == []
    }

    def formatsUsageMessage() {
        parser.option('a', 'long-option').hasDescription('this is option a')
        parser.option('b')
        parser.option('another-long-option').hasDescription('this is a long option')
        parser.option('z', 'y', 'last-option', 'end-option').hasDescription('this is the last option')
        parser.option('B')
        def outstr = new StringWriter()

        expect:
        parser.printUsage(outstr)
        outstr.toString().readLines() == [
            '-a, --long-option                    this is option a',
            '--another-long-option                this is a long option',
            '-B',
            '-b',
            '-y, -z, --end-option, --last-option  this is the last option',
            '--                                   Signals the end of built-in options. Gradle parses subsequent parameters as only tasks or task options.'
        ]
    }

    def formatsUsageMessageForDeprecatedAndIncubatingOptions() {
        parser.option('a', 'long-option').hasDescription('this is option a').deprecated()
        parser.option('b').deprecated()
        parser.option('c').hasDescription('option c').incubating()
        parser.option('d').incubating()
        def outstr = new StringWriter()

        expect:
        parser.printUsage(outstr)
        outstr.toString().readLines() == [
            '-a, --long-option  this is option a [deprecated]',
            '-b                 [deprecated]',
            '-c                 option c [incubating]',
            '-d                 [incubating]',
            '--                 Signals the end of built-in options. Gradle parses subsequent parameters as only tasks or task options.'
        ]
    }

    def parseFailsWhenCommandLineContainsUnknownShortOption() {
        when:
        parser.parse(['-a'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'-a\'.'
    }

    def parseFailsWhenCommandLineContainsUnknownShortOptionWithDoubleDashes() {
        when:
        parser.parse(['--a'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'--a\'.'
    }

    def parseFailsWhenCommandLineContainsUnknownShortOptionWithEqualsArgument() {
        when:
        parser.parse(['-a=arg'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'-a\'.'
    }

    def parseFailsWhenCommandLineContainsUnknownShortOptionWithAttachedArgument() {
        when:
        parser.parse(['-aarg'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'-a\'.'
    }

    def parseFailsWhenCommandLineContainsUnknownLongOption() {
        when:
        parser.parse(['--unknown'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'--unknown\'.'
    }

    def parseFailsWhenCommandLineContainsUnknownLongOptionWithSingleDashes() {
        when:
        parser.parse(['-unknown'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'-u\'.'
    }

    def "parse fails when command line contains unknown option with newline #arg"() {
        when:
        parser.parse(arg)

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == "Unknown command-line option '${reportedAs}'."

        where:
        arg                | reportedAs
        '-a\nb'            | '-a'
        '--a\nb'           | '--a\nb'
        '--a\nb=something' | '--a\nb'
        '-\n'              | '-\n'
        '-\na'             | '-\n'
        '--\n'             | '--\n'
        '--\n=nothing'     | '--\n'
    }

    def parseFailsWhenCommandLineContainsUnknownLongOptionWithEqualsArgument() {
        when:
        parser.parse(['--unknown=arg'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'--unknown\'.'
    }

    def parseFailsWhenCommandLineContainsLongOptionWithAttachedArgument() {
        parser.option("long").hasArgument()

        when:
        parser.parse(['--longvalue'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'--longvalue\'.'
    }

    def parseFailsWhenCommandLineContainsDashAndEquals() {
        parser.option("long").hasArgument()

        when:
        parser.parse(['-='])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'-=\'.'
    }

    def getOptionFailsForUnknownOption() {
        def result = parser.parse(['other'])

        when:
        result.option('unknown')

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Option \'unknown\' not defined.'

        when:
        result.hasOption('unknown')

        then:
        e = thrown(IllegalArgumentException)
        e.message == 'Option \'unknown\' not defined.'
    }

    def parseFailsWhenSingleValueOptionHasMultipleArguments() {
        parser.option('a').hasArgument()

        when:
        parser.parse(['-a=arg1', '-a', 'arg2'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Multiple arguments were provided for command-line option \'-a\'.'
    }

    def parseFailsWhenArgumentIsMissing() {
        parser.option('a').hasArgument().hasDescription("No argument description.")
        when:
        parser.parse(['-a'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'No argument was provided for command-line option \'-a\' with description: \'No argument description.\''
    }

    def parseFailsWhenArgumentIsMissingFromEqualsForm() {
        parser.option('a').hasArgument()

        when:
        parser.parse(['-a='])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'An empty argument was provided for command-line option \'-a\'.'
    }

    def parseAcceptsMultilineArgument() {
        parser.option('D').hasArgument()

        expect:
        def result = parser.parse(['-Dprops=a:1\nb:2\nc:3'])
        result.option('D').values == ['props=a:1\nb:2\nc:3']
    }

    def parseAcceptsMultilineArgumentForLongOption() {
        parser.option('a', 'long-option').hasArgument()

        expect:
        def result = parser.parse(['--long-option=a\nb\nc'])
        result.option('long-option').values == ['a\nb\nc']
    }

    def parseFailsWhenEmptyArgumentIsProvided() {
        parser.option('a').hasArgument()

        when:
        parser.parse(['-a', ''])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'An empty argument was provided for command-line option \'-a\'.'
    }

    def parseFailsWhenArgumentIsMissingAndAnotherOptionFollows() {
        parser.option('a').hasArgument().hasDescription("No argument description.")

        when:
        parser.parse(['-a', '-b'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'No argument was provided for command-line option \'-a\' with description: \'No argument description.\''
    }

    def parseFailsWhenArgumentIsMissingAndOptionsAreCombined() {
        parser.option('a')
        parser.option('b').hasArgument().hasDescription("No argument description.")

        when:
        parser.parse(['-ab'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'No argument was provided for command-line option \'-b\' with description: \'No argument description.\''
    }

    def parseFailsWhenAttachedArgumentIsProvidedForOptionWhichDoesNotTakeAnArgument() {
        parser.option('a')

        when:
        parser.parse(['-aarg'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Unknown command-line option \'-r\'.'
    }

    def parseFailsWhenEqualsArgumentIsProvidedForOptionWhichDoesNotTakeAnArgument() {
        parser.option('a')

        when:
        parser.parse(['-a=arg'])

        then:
        def e = thrown(CommandLineArgumentException)
        e.message == 'Command-line option \'-a\' does not take an argument.'
    }

    def "allow unknown options mode collects unknown options"() {
        given:
        parser.option("a")

        and:
        parser.allowUnknownOptions()

        when:
        def result = parser.parse(['-a', '-b', '--long-option'])

        then:
        result.option("a") != null

        and:
        result.extraArguments == ['-b', '--long-option']
    }

    def "allow unknown options mode collects unknown short options combined with known short options"() {
        given:
        parser.option("a")
        parser.option("Z")

        and:
        parser.allowUnknownOptions()

        when:
        def result = parser.parse(['-abCdZ'])

        then:
        result.option("a") != null
        result.option("Z") != null

        and:
        result.extraArguments == ["-b", "-C", "-d"]
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-1871")
    def "unknown options containing known arguments in their value are allowed"() {
        given:
        parser.option("a")

        and:
        parser.allowUnknownOptions()

        when:
        def result = parser.parse(['-a', '-ba', '-ba=c'])

        then:
        result.option("a") != null

        and:
        result.extraArguments == ['-ba', '-ba=c']
    }

    def parseExtraArguments() {
        when:
        def result = parser.parse(['arg1', 'arg\ntwo'])

        then:
        result.extraArguments == ['arg1', 'arg\ntwo']
    }

    def "group boolean opposite option pairs together"() {
        parser.option('a-option').hasDescription('this is option --a-option')
        parser.option('a-option-other').hasDescription('this is option --a-option-other')
        parser.option('no-a-option').hasDescription('Disables option --a-option')
        parser.option('c-option')
        parser.option('no-c-option')
        def outstr = new StringWriter()

        expect:
        parser.printUsage(outstr)
        outstr.toString().readLines() == [
            '--a-option        this is option --a-option',
            '--no-a-option     Disables option --a-option',
            '--a-option-other  this is option --a-option-other',
            '--c-option',
            '--no-c-option',
            '--                Signals the end of built-in options. Gradle parses subsequent parameters as only tasks or task options.'
        ]
    }
}
