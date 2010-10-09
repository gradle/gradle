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
package org.gradle.initialization;

import org.gradle.CommandLineArgumentException;
import org.gradle.util.GUtil;

import java.io.OutputStream;
import java.util.*;

/**
 * <p>A command-line parser which supports a command/subcommand style command-line interface. Supports the following
 * syntax:</p>
 * <pre>
 * &lt;option>* (&lt;subcommand> &lt;subcommand-option>*)*
 * </pre>
 *
 * <ul> <li>Short options are a '-' followed by a single character. For example: {@code -a}.</li>
 *
 * <li>Long options are '--' followed by multiple characters. For example: {@code --long-option}.</li>
 *
 * <li>Options can take arguments. The argument follows the option. For example: {@code -a arg} or {@code --long
 * arg}.</li>
 *
 * <li>Arguments can be attached to the option using '='. For example: {@code -a=arg} or {@code --long=arg}.</li>
 *
 * <li>Arguments can be attached to short options. For example: {@code -aarg}.</li>
 *
 * <li>Short options can be combined. For example {@code -ab} is equivalent to {@code -a -b}.</li>
 *
 * <li>Anything else is treated as an extra argument. This includes a single {@code -} character.</li>
 *
 * <li>'--' indicates the end of the options. Anything following is not parsed and is treated as extra arguments.</li>
 *
 * <li>The parser is forgiving, and allows '--' to be used with short options and '-' to be used with long
 * options.</li>
 *
 * <li>subcommands and their options do not need to be known at parse time.</li> </ul>
 */
public class CommandLineParser {
    private Map<String, CommandLineOption> optionsByString = new HashMap<String, CommandLineOption>();
    private boolean allowMixedOptions;

    /**
     * Parses the given command-line.
     *
     * @param commandLine The command-line.
     * @return The parsed command line.
     * @throws org.gradle.CommandLineArgumentException
     *          On parse failure.
     */
    public ParsedCommandLine parse(String[] commandLine) throws CommandLineArgumentException {
        return parse(Arrays.asList(commandLine));
    }

    /**
     * Parses the given command-line.
     *
     * @param commandLine The command-line.
     * @return The parsed command line.
     * @throws org.gradle.CommandLineArgumentException
     *          On parse failure.
     */
    public ParsedCommandLine parse(Iterable<String> commandLine) {
        ParsedCommandLine parsedCommandLine = new ParsedCommandLine(new HashSet<CommandLineOption>(optionsByString.values()));
        ParseState parseState = new BeforeSubcommand(parsedCommandLine);
        for (String arg : commandLine) {
            if (parseState.maybeStartOption(arg)) {
                if (arg.equals("--")) {
                    parseState = new OptionsComplete(parsedCommandLine);
                } else if (arg.matches("--[^=]+")) {
                    OptionParseState parsedOption = parseState.addOption(arg, arg.substring(2));
                    parseState = parsedOption.asNextArg();
                } else if (arg.matches("--[^=]+=.*")) {
                    int endArg = arg.indexOf('=');
                    OptionParseState parsedOption = parseState.addOption(arg, arg.substring(2, endArg));
                    parsedOption.addArgument(arg.substring(endArg + 1));
                } else if (arg.matches("-[^=]=.*")) {
                    OptionParseState parsedOption = parseState.addOption(arg, arg.substring(1, 2));
                    parsedOption.addArgument(arg.substring(3));
                } else {
                    assert arg.matches("-[^-].*");
                    String option = arg.substring(1);
                    if (optionsByString.containsKey(option)) {
                        OptionParseState parsedOption = parseState.addOption(arg, option);
                        parseState = parsedOption.asNextArg();
                    } else {
                        String option1 = arg.substring(1, 2);
                        OptionParseState parsedOption = parseState.addOption(arg, option1);
                        if (parsedOption.getHasArgument()) {
                            parsedOption.addArgument(arg.substring(2));
                        } else {
                            for (int i = 2; i < arg.length(); i++) {
                                String optionStr = arg.substring(i, i + 1);
                                parsedOption = parseState.addOption(arg, optionStr);
                                parsedOption.argumentMissing();
                            }
                        }
                    }
                }
            } else {
                parseState = parseState.onExtraValue(arg);
            }
        }

        parseState.complete();
        return parsedCommandLine;
    }

    public CommandLineParser allowMixedSubcommandsAndOptions() {
        allowMixedOptions = true;
        return this;
    }

    /**
     * Prints a usage message to the given stream.
     *
     * @param out The output stream to write to.
     */
    public void printUsage(OutputStream out) {
        Formatter formatter = new Formatter(out);
        Set<CommandLineOption> orderedOptions = new TreeSet<CommandLineOption>(new OptionComparator());
        orderedOptions.addAll(optionsByString.values());
        Map<String, String> lines = new LinkedHashMap<String, String>();
        for (CommandLineOption option : orderedOptions) {
            Set<String> orderedOptionStrings = new TreeSet<String>(new OptionStringComparator());
            orderedOptionStrings.addAll(option.getOptions());
            List<String> prefixedStrings = new ArrayList<String>();
            for (String optionString : orderedOptionStrings) {
                if (optionString.length() == 1) {
                    prefixedStrings.add("-" + optionString);
                } else {
                    prefixedStrings.add("--" + optionString);
                }
            }
            lines.put(GUtil.join(prefixedStrings, ", "), GUtil.elvis(option.getDescription(), ""));
        }
        int max = 0;
        for (String optionStr : lines.keySet()) {
            max = Math.max(max, optionStr.length());
        }
        for (Map.Entry<String, String> entry : lines.entrySet()) {
            if (entry.getValue().length() == 0) {
                formatter.format("%s%n", entry.getKey());
            } else {
                formatter.format("%-" + max + "s  %s%n", entry.getKey(), entry.getValue());
            }
        }
        formatter.flush();
    }

    /**
     * Defines a new option. By default, the option takes no arguments and has no description.
     *
     * @param options The options values.
     * @return The option, which can be further configured.
     */
    public CommandLineOption option(String... options) {
        for (String option : options) {
            if (optionsByString.containsKey(option)) {
                throw new IllegalArgumentException(String.format("Option '%s' is already defined.", option));
            }
            if (option.startsWith("-")) {
                throw new IllegalArgumentException(String.format("Cannot add option '%s' as an option cannot start with '-'.", option));
            }
        }
        CommandLineOption option = new CommandLineOption(Arrays.asList(options));
        for (String optionStr : option.getOptions()) {
            this.optionsByString.put(optionStr, option);
        }
        return option;
    }

    private static class OptionString {
        private final String arg;
        private final String option;

        private OptionString(String arg, String option) {
            this.arg = arg;
            this.option = option;
        }

        public String getDisplayName() {
            return arg.startsWith("--") ? "--" + option : "-" + option;
        }

        @Override
        public String toString() {
            return getDisplayName();
        }
    }

    private static abstract class ParseState {
        public boolean maybeStartOption(String arg) {
            return arg.matches("-.+");
        }

        public abstract ParseState onExtraValue(String arg);

        public void complete() {
        }

        public OptionParseState addOption(String arg, String option) {
            return addOption(new OptionString(arg, option));
        }

        public abstract OptionParseState addOption(OptionString option);
    }

    private static class OptionParseState extends ParseState {
        private final ParsedCommandLineOption option;
        private final OptionString actualOption;
        private final ParseState nextState;

        private OptionParseState(ParsedCommandLineOption option, OptionString actualOption, ParseState nextState) {
            this.option = option;
            this.actualOption = actualOption;
            this.nextState = nextState;
        }

        @Override
        public boolean maybeStartOption(String arg) {
            if (super.maybeStartOption(arg)) {
                argumentMissing();
                return true;
            } else {
                return false;
            }
        }

        @Override
        public ParseState onExtraValue(String arg) {
            addArgument(arg);
            return nextState;
        }

        @Override
        public void complete() {
            argumentMissing();
        }

        @Override
        public OptionParseState addOption(OptionString option) {
            throw new UnsupportedOperationException();
        }

        public void addArgument(String argument) {
            if (!getHasArgument()) {
                throw new CommandLineArgumentException(String.format("Command-line option '%s' does not take an argument.", actualOption));
            }
            if (argument.length() == 0) {
                throw new CommandLineArgumentException(String.format("An empty argument was provided for command-line option '%s'.", actualOption));
            }
            if (!option.getValues().isEmpty() && !option.getOption().getAllowsMultipleArguments()) {
                throw new CommandLineArgumentException(String.format("Multiple arguments were provided for command-line option '%s'.", actualOption));
            }
            option.addArgument(argument);
        }

        public boolean getHasArgument() {
            return option.getOption().getAllowsArguments();
        }

        public ParseState asNextArg() {
            return getHasArgument() ? this : nextState;
        }

        public void argumentMissing() {
            if (!getHasArgument()) {
                return;
            }
            throw new CommandLineArgumentException(String.format("No argument was provided for command-line option '%s'.", actualOption));
        }
    }

    private static class GlobalParseState extends ParseState {
        final ParsedCommandLine commandLine;

        GlobalParseState(ParsedCommandLine commandLine) {
            this.commandLine = commandLine;
        }

        @Override
        public OptionParseState addOption(OptionString option) {
            ParsedCommandLineOption parsedOption = commandLine.addOption(option.option);
            if (parsedOption == null) {
                return onUnknownOption(option);
            }
            if (parsedOption.getOption().getSubcommand() != null) {
                ParseState nextState = onExtraValue(parsedOption.getOption().getSubcommand());
                return new OptionParseState(parsedOption, option, nextState);
            }
            return new OptionParseState(parsedOption, option, this);
        }

        OptionParseState onUnknownOption(OptionString option) {
            throw new CommandLineArgumentException(String.format("Unknown command-line option '%s'.", option));
        }

        @Override
        public ParseState onExtraValue(String arg) {
            commandLine.addExtraValue(arg);
            return this;
        }
    }

    private class BeforeSubcommand extends GlobalParseState {
        BeforeSubcommand(ParsedCommandLine commandLine) {
            super(commandLine);
        }

        @Override
        public ParseState onExtraValue(String arg) {
            super.onExtraValue(arg);
            return new AfterSubcommand(commandLine);
        }
    }

    private class AfterSubcommand extends GlobalParseState {
        AfterSubcommand(ParsedCommandLine commandLine) {
            super(commandLine);
        }

        @Override
        public OptionParseState addOption(OptionString option) {
            if (allowMixedOptions) {
                return super.addOption(option);
            } else {
                return onUnknownOption(option);
            }
        }

        @Override
        OptionParseState onUnknownOption(OptionString option) {
            commandLine.addExtraValue(option.arg);
            return new OptionParseState(new ParsedCommandLineOption(new CommandLineOption(Collections.singleton(option.option))), option, this);
        }
    }

    private static class OptionsComplete extends GlobalParseState {
        OptionsComplete(ParsedCommandLine commandLine) {
            super(commandLine);
        }

        @Override
        public boolean maybeStartOption(String arg) {
            return false;
        }
    }

    private static final class OptionComparator implements Comparator<CommandLineOption> {
        public int compare(CommandLineOption option1, CommandLineOption option2) {
            String min1 = Collections.min(option1.getOptions(), new OptionStringComparator());
            String min2 = Collections.min(option2.getOptions(), new OptionStringComparator());
            return new CaseInsensitiveStringComparator().compare(min1, min2);
        }
    }

    private static final class CaseInsensitiveStringComparator implements Comparator<String> {
        public int compare(String option1, String option2) {
            int diff = option1.compareToIgnoreCase(option2);
            if (diff != 0) {
                return diff;
            }
            return option1.compareTo(option2);
        }
    }

    private static final class OptionStringComparator implements Comparator<String> {
        public int compare(String option1, String option2) {
            boolean short1 = option1.length() == 1;
            boolean short2 = option2.length() == 1;
            if (short1 && !short2) {
                return -1;
            }
            if (!short1 && short2) {
                return 1;
            }
            return new CaseInsensitiveStringComparator().compare(option1, option2);
        }
    }
}
