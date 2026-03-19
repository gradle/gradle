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
package org.gradle.cli;

import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>A command-line parser which supports a command/sub-command style command-line interface. Supports the following
 * syntax:</p>
 * <pre>
 * &lt;option&gt;* (&lt;sub-command&gt; &lt;sub-command-option&gt;*)*
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
 * <li>The set of options must be known at parse time. Sub-commands and their options do not need to be known at parse
 * time. Use {@link ParsedCommandLine#getExtraArguments()} to obtain the non-option command-line arguments.</li>
 *
 * </ul>
 */
public class CommandLineParser {
    private static final Pattern OPTION_NAME_PATTERN = Pattern.compile("(\\?|\\p{Alnum}[\\p{Alnum}-_]*)");

    private static final String DISABLE_OPTION_PREFIX = "no-";

    private Map<String, CommandLineOption> optionsByString = new HashMap<String, CommandLineOption>();
    private boolean allowMixedOptions;
    private boolean allowUnknownOptions;

    /**
     * Parses the given command-line.
     *
     * @param commandLine The command-line.
     * @return The parsed command line.
     * @throws org.gradle.cli.CommandLineArgumentException
     *          On parse failure.
     */
    public ParsedCommandLine parse(String... commandLine) throws CommandLineArgumentException {
        return parse(Arrays.asList(commandLine));
    }

    /**
     * Parses the given command-line.
     *
     * @param commandLine The command-line.
     * @return The parsed command line.
     * @throws org.gradle.cli.CommandLineArgumentException
     *          On parse failure.
     */
    public ParsedCommandLine parse(Iterable<String> commandLine) throws CommandLineArgumentException {
        ParsedCommandLine parsedCommandLine = new ParsedCommandLine(new HashSet<CommandLineOption>(optionsByString.values()));
        ParserState parseState = new BeforeFirstSubCommand(parsedCommandLine);
        for (String arg : commandLine) {
            if (parseState.maybeStartOption(arg)) {
                if (arg.equals("--")) {
                    parseState = new AfterOptions(parsedCommandLine);
                } else if (arg.matches("--[^=]+")) {
                    OptionParserState parsedOption = parseState.onStartOption(arg, arg.substring(2));
                    parseState = parsedOption.onStartNextArg();
                } else if (arg.matches("(?s)--[^=]+=.*")) {
                    int endArg = arg.indexOf('=');
                    OptionParserState parsedOption = parseState.onStartOption(arg, arg.substring(2, endArg));
                    parseState = parsedOption.onArgument(arg.substring(endArg + 1));
                } else if (arg.matches("(?s)-[^=]=.*")) {
                    OptionParserState parsedOption = parseState.onStartOption(arg, arg.substring(1, 2));
                    parseState = parsedOption.onArgument(arg.substring(3));
                } else {
                    assert arg.matches("(?s)-[^-].*");
                    String option = arg.substring(1);
                    if (optionsByString.containsKey(option)) {
                        OptionParserState parsedOption = parseState.onStartOption(arg, option);
                        parseState = parsedOption.onStartNextArg();
                    } else {
                        String option1 = arg.substring(1, 2);
                        OptionParserState parsedOption;
                        if (optionsByString.containsKey(option1)) {
                            parsedOption = parseState.onStartOption("-" + option1, option1);
                            if (parsedOption.getHasArgument()) {
                                parseState = parsedOption.onArgument(arg.substring(2));
                            } else {
                                parseState = parsedOption.onComplete();
                                for (int i = 2; i < arg.length(); i++) {
                                    String optionStr = arg.substring(i, i + 1);
                                    parsedOption = parseState.onStartOption("-" + optionStr, optionStr);
                                    parseState = parsedOption.onComplete();
                                }
                            }
                        } else {
                            if (allowUnknownOptions) {
                                // if we are allowing unknowns, just pass through the whole arg
                                parsedOption = parseState.onStartOption(arg, option);
                                parseState = parsedOption.onComplete();
                            } else {
                                // We are going to throw a CommandLineArgumentException below, but want the message
                                // to reflect that we didn't recognise the first char (i.e. the option specifier)
                                parsedOption = parseState.onStartOption("-" + option1, option1);
                                parseState = parsedOption.onComplete();
                            }
                        }
                    }
                }
            } else {
                parseState = parseState.onNonOption(arg);
            }
        }

        parseState.onCommandLineEnd();
        return parsedCommandLine;
    }

    public CommandLineParser allowMixedSubcommandsAndOptions() {
        allowMixedOptions = true;
        return this;
    }

    public CommandLineParser allowUnknownOptions() {
        allowUnknownOptions = true;
        return this;
    }

    /**
     * Specifies that the given set of options are mutually-exclusive. Only one of the given options will be selected.
     * The parser ignores all but the last of these options.
     */
    public CommandLineParser allowOneOf(String... options) {
        Set<CommandLineOption> commandLineOptions = new HashSet<CommandLineOption>();
        for (String option : options) {
            commandLineOptions.add(optionsByString.get(option));
        }
        for (CommandLineOption commandLineOption : commandLineOptions) {
            commandLineOption.groupWith(commandLineOptions);
        }
        return this;
    }

    /**
     * Prints a usage message to the given stream.
     *
     * @param out The output stream to write to.
     */
    @SuppressWarnings("NullAway")
    public void printUsage(Appendable out, int widthHint) {
        // sort options before grouping
        Set<CommandLineOption> commandLineOptions = new TreeSet<>(new OptionComparator());
        commandLineOptions.addAll(optionsByString.values());

        // map options to their categories
        Map<OptionCategory, List<RenderedCommandLineOption>> categoryToOptions = new EnumMap<>(OptionCategory.class);
        for (OptionCategory category : OptionCategory.values()) {
            categoryToOptions.put(category, new ArrayList<>());
        }
        for (CommandLineOption option : commandLineOptions) {
            categoryToOptions.get(option.getCategory()).add(RenderedCommandLineOption.from(option));
        }

        // calculate column widths for option name and description
        int nameColumnWidth = categoryToOptions.values().stream()
            .flatMap(List::stream)
            .mapToInt(o -> o.getName().length())
            .max()
            .orElse(0) + 3; // account for two extra spaces before each option plus an extra space between the longest option and its description
        int descriptionColumnWidth = Math.max(30, widthHint - nameColumnWidth);

        // print hint about end signal
        Formatter formatter = new Formatter(out);
        printRenderedOption(formatter, "--", nameColumnWidth, "Signals the end of built-in options. Parses subsequent parameters as tasks or task options only.", descriptionColumnWidth);

        // print each category and its options
        for (OptionCategory category : OptionCategory.values()) {
            List<RenderedCommandLineOption> options = categoryToOptions.get(category);
            if (options == null || options.isEmpty()) {
                continue;
            }

            // print category name
            String categoryName = category.getDisplayName();
            if (!categoryName.isEmpty()) {
                printCategory(formatter, categoryName);
            }

            // print option name and description
            for (RenderedCommandLineOption option : options) {
                String name = option.getName();
                String description = option.getDescription();
                printRenderedOption(formatter, "  " + name, nameColumnWidth, description, descriptionColumnWidth);
            }
        }
        formatter.flush();
    }

    private static void printRenderedOption(Formatter formatter, String name, int nameColumnWidth, String description, int descriptionColumnWidth) {
        if (description == null || description.isEmpty()) {
            printOption(formatter, name);
        } else {
            // handle multi-line descriptions and split lines that are too long for the console
            List<String> descriptionLines = Arrays.stream(description.split("\\r?\\n"))
                .flatMap(n -> splitToLength(n, descriptionColumnWidth).stream())
                .collect(Collectors.toList());
            for (int i = 0; i < descriptionLines.size(); i++) {
                printOption(formatter, i == 0 ? name : "", nameColumnWidth, descriptionLines.get(i));
            }
        }
    }

    public static List<String> splitToLength(String input, int n) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        while (start < input.length()) {
            int end = Math.min(start + n, input.length());
            if (end < input.length()) {
                int lastSpace = input.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }
            lines.add(input.substring(start, end));
            start = end;
            // skip whitespace at beginning of next line
            while (start < input.length() && Character.isWhitespace(input.charAt(start))) {
                start++;
            }
        }
        return lines;
    }

    private static void printCategory(Formatter formatter, String name) {
        formatter.format("%n%s:%n", name);
    }

    private static void printOption(Formatter formatter, String name) {
        formatter.format("%s%n", name);
    }

    private static void printOption(Formatter formatter, String name, int nameColumnWidth, String description) {
        formatter.format("%-" + nameColumnWidth + "s %s%n", name, description);
    }

    @NullMarked
    private static class RenderedCommandLineOption {
        private final String name;
        private final String description;

        private RenderedCommandLineOption(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        static RenderedCommandLineOption from(CommandLineOption option) {
            return new RenderedCommandLineOption(optionName(option), option.getDescription());
        }

        private static String optionName(CommandLineOption option) {
            Set<String> optionStrings = new TreeSet<>(new OptionStringComparator());
            optionStrings.addAll(option.getOptions());
            List<String> prefixedStrings = new ArrayList<>();
            for (String optionString : optionStrings) {
                if (optionString.length() == 1) {
                    prefixedStrings.add("-" + optionString);
                } else {
                    prefixedStrings.add("--" + optionString);
                }
            }

            String key = join(prefixedStrings, ", ");
            return key;
        }

        private static String join(Collection<?> things, String separator) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;

            if (separator == null) {
                separator = "";
            }

            for (Object thing : things) {
                if (!first) {
                    builder.append(separator);
                }
                builder.append(thing.toString());
                first = false;
            }
            return builder.toString();
        }
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
            if (!OPTION_NAME_PATTERN.matcher(option).matches()) {
                throw new IllegalArgumentException(String.format("Cannot add option '%s' as an option can only contain alphanumeric characters or '-' or '_'.", option));
            }
        }
        CommandLineOption option = new CommandLineOption(Arrays.asList(options));
        for (String optionStr : option.getOptions()) {
            optionsByString.put(optionStr, option);
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

    private static abstract class ParserState {
        public abstract boolean maybeStartOption(String arg);

        boolean isOption(String arg) {
            return arg.matches("(?s)-.+");
        }

        public abstract OptionParserState onStartOption(String arg, String option);

        public abstract ParserState onNonOption(String arg);

        public void onCommandLineEnd() {
        }
    }

    private abstract class OptionAwareParserState extends ParserState {
        protected final ParsedCommandLine commandLine;

        protected OptionAwareParserState(ParsedCommandLine commandLine) {
            this.commandLine = commandLine;
        }

        @Override
        public boolean maybeStartOption(String arg) {
            return isOption(arg);
        }

        @Override
        public ParserState onNonOption(String arg) {
            commandLine.addExtraValue(arg);
            return allowMixedOptions ? new AfterFirstSubCommand(commandLine) : new AfterOptions(commandLine);
        }
    }

    private class BeforeFirstSubCommand extends OptionAwareParserState {
        private BeforeFirstSubCommand(ParsedCommandLine commandLine) {
            super(commandLine);
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            OptionString optionString = new OptionString(arg, option);
            CommandLineOption commandLineOption = optionsByString.get(option);
            if (commandLineOption == null) {
                if (allowUnknownOptions) {
                    return new UnknownOptionParserState(arg, commandLine, this);
                } else {
                    throw new CommandLineArgumentException(String.format("Unknown command-line option '%s'.", optionString));
                }
            }
            return new KnownOptionParserState(optionString, commandLineOption, commandLine, this);
        }
    }

    private class AfterFirstSubCommand extends OptionAwareParserState {
        private AfterFirstSubCommand(ParsedCommandLine commandLine) {
            super(commandLine);
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            CommandLineOption commandLineOption = optionsByString.get(option);
            if (commandLineOption == null) {
                return new UnknownOptionParserState(arg, commandLine, this);
            }
            return new KnownOptionParserState(new OptionString(arg, option), commandLineOption, commandLine, this);
        }
    }

    private static class AfterOptions extends ParserState {
        private final ParsedCommandLine commandLine;

        private AfterOptions(ParsedCommandLine commandLine) {
            this.commandLine = commandLine;
        }

        @Override
        public boolean maybeStartOption(String arg) {
            return false;
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            return new UnknownOptionParserState(arg, commandLine, this);
        }

        @Override
        public ParserState onNonOption(String arg) {
            commandLine.addExtraValue(arg);
            return this;
        }
    }

    private static class MissingOptionArgState extends ParserState {
        private final OptionParserState option;

        private MissingOptionArgState(OptionParserState option) {
            this.option = option;
        }

        @Override
        public boolean maybeStartOption(String arg) {
            return isOption(arg);
        }

        @Override
        public OptionParserState onStartOption(String arg, String option) {
            return this.option.onComplete().onStartOption(arg, option);
        }

        @Override
        public ParserState onNonOption(String arg) {
            return option.onArgument(arg);
        }

        @Override
        public void onCommandLineEnd() {
            option.onComplete();
        }
    }

    private static abstract class OptionParserState {
        public abstract ParserState onStartNextArg();

        public abstract ParserState onArgument(String argument);

        public abstract boolean getHasArgument();

        public abstract ParserState onComplete();
    }

    private static class KnownOptionParserState extends OptionParserState {
        private final OptionString optionString;
        private final CommandLineOption option;
        private final ParsedCommandLine commandLine;
        private final ParserState state;
        private final List<String> values = new ArrayList<String>();

        private KnownOptionParserState(OptionString optionString, CommandLineOption option, ParsedCommandLine commandLine, ParserState state) {
            this.optionString = optionString;
            this.option = option;
            this.commandLine = commandLine;
            this.state = state;
        }

        @Override
        public ParserState onArgument(String argument) {
            if (!getHasArgument()) {
                throw new CommandLineArgumentException(String.format("Command-line option '%s' does not take an argument.", optionString));
            }
            if (argument.length() == 0) {
                throw new CommandLineArgumentException(String.format("An empty argument was provided for command-line option '%s'.", optionString));
            }
            values.add(argument);
            return onComplete();
        }

        @Override
        public ParserState onStartNextArg() {
            if (option.getAllowsArguments() && values.isEmpty()) {
                return new MissingOptionArgState(this);
            }
            return onComplete();
        }

        @Override
        public boolean getHasArgument() {
            return option.getAllowsArguments();
        }

        @Override
        public ParserState onComplete() {
            if (getHasArgument() && values.isEmpty()) {
                throw new CommandLineArgumentException(String.format("No argument was provided for command-line option '%s' with description: '%s'", optionString, option.getDescription()));
            }

            ParsedCommandLineOption parsedOption = commandLine.addOption(optionString.option, option);
            if (values.size() + parsedOption.getValues().size() > 1 && !option.getAllowsMultipleArguments()) {
                throw new CommandLineArgumentException(String.format("Multiple arguments were provided for command-line option '%s'.", optionString));
            }
            for (String value : values) {
                parsedOption.addArgument(value);
            }

            for (CommandLineOption otherOption : option.getGroupWith()) {
                commandLine.removeOption(otherOption);
            }

            return state;
        }
    }

    private static class UnknownOptionParserState extends OptionParserState {
        private final ParserState state;
        private final String arg;
        private final ParsedCommandLine commandLine;

        private UnknownOptionParserState(String arg, ParsedCommandLine commandLine, ParserState state) {
            this.arg = arg;
            this.commandLine = commandLine;
            this.state = state;
        }

        @Override
        public boolean getHasArgument() {
            return true;
        }

        @Override
        public ParserState onStartNextArg() {
            return onComplete();
        }

        @Override
        public ParserState onArgument(String argument) {
            return onComplete();
        }

        @Override
        public ParserState onComplete() {
            commandLine.addExtraValue(arg);
            return state;
        }
    }

    private static final class OptionComparator implements Comparator<CommandLineOption> {
        @Override
        public int compare(CommandLineOption option1, CommandLineOption option2) {
            String min1 = Collections.min(option1.getOptions(), new OptionStringComparator());
            String min2 = Collections.min(option2.getOptions(), new OptionStringComparator());
            // Group opposite option pairs together
            min1 = min1.startsWith(DISABLE_OPTION_PREFIX) ? min1.substring(DISABLE_OPTION_PREFIX.length()) + "-" : min1;
            min2 = min2.startsWith(DISABLE_OPTION_PREFIX) ? min2.substring(DISABLE_OPTION_PREFIX.length()) + "-" : min2;
            return new CaseInsensitiveStringComparator().compare(min1, min2);
        }
    }

    private static final class CaseInsensitiveStringComparator implements Comparator<String> {
        @Override
        public int compare(String option1, String option2) {
            int diff = option1.compareToIgnoreCase(option2);
            if (diff != 0) {
                return diff;
            }
            return option1.compareTo(option2);
        }
    }

    private static final class OptionStringComparator implements Comparator<String> {
        @Override
        public int compare(String option1, String option2) {
            boolean short1 = option1.length() == 1;
            boolean short2 = option2.length() == 1;
            if (short1 && !short2) {
                return 1;
            }
            if (!short1 && short2) {
                return -1;
            }
            return new CaseInsensitiveStringComparator().compare(option1, option2);
        }
    }
}
