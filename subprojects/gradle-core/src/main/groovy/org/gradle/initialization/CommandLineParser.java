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
 * A GNU-style command-line parser.
 *
 * <ul>
 * <li>Short options are a '-' followed by a single character. For example: '-a'.</li>
 * <li>Long options are '--' followed by multiple characters. For example: '--long-option'.</li>
 * <li>Options can take arguments. The argument follows the option. For example: '-a arg' or '--long arg'.</li>
 * <li>Arguments can be attached to the option using '='. For example: '-a=arg' or '--long=arg'.</li>
 * <li>Arguments can be attached to short options. For example: '-aarg'.</li>
 * <li>Short options can be combined. For example '-ab' is equivalent to '-a -b'.</li>
 * <li>Anything else is treated as an extra argument. This includes a single '-'.</li>
 * <li>'--' indicates the end of the options. Anything following is not parsed and is treated as extra arguments
 * .</li>
 * </ul>
 */
public class CommandLineParser {
    private Map<String, CommandLineOption> optionsByString = new HashMap<String, CommandLineOption>();

    public ParsedCommandLine parse(String[] commandLine) {
        return parse(Arrays.asList(commandLine));
    }

    public ParsedCommandLine parse(Iterable<String> commandLine) {
        ParsedCommandLine parsedCommandLine = new ParsedCommandLine(new HashSet<CommandLineOption>(optionsByString.values()));
        OptionParseState needArgFor = null;
        boolean optionsFinished = false;
        for (String arg : commandLine) {
            if (!optionsFinished && arg.matches("-.+")) {
                if (needArgFor != null) {
                    needArgFor.argumentMissing();
                }
                if (arg.equals("--")) {
                    optionsFinished = true;
                } else if (arg.matches("--[^=]+")) {
                    OptionParseState parsedOption = addOption(parsedCommandLine, arg, arg.substring(2));
                    needArgFor = parsedOption.asNextArg();
                } else if (arg.matches("--[^=]+=.*")) {
                    int endArg = arg.indexOf('=');
                    OptionParseState parsedOption = addOption(parsedCommandLine, arg, arg.substring(2, endArg));
                    parsedOption.addArgument(arg.substring(endArg + 1));
                } else if (arg.matches("-[^=]=.*")) {
                    OptionParseState parsedOption = addOption(parsedCommandLine, arg, arg.substring(1, 2));
                    parsedOption.addArgument(arg.substring(3));
                } else {
                    assert arg.matches("-[^-].*");
                    String option = arg.substring(1);
                    if (optionsByString.containsKey(option)) {
                        OptionParseState parsedOption = addOption(parsedCommandLine, arg, option);
                        needArgFor = parsedOption.asNextArg();
                    } else {
                        String option1 = arg.substring(1, 2);
                        OptionParseState parsedOption = addOption(parsedCommandLine, arg, option1);
                        if (parsedOption.getHasArgument()) {
                            parsedOption.addArgument(arg.substring(2));
                        } else {
                            for (int i = 2; i < arg.length(); i++) {
                                String optionStr = arg.substring(i, i + 1);
                                parsedOption = addOption(parsedCommandLine, arg, optionStr);
                                parsedOption.argumentMissing();
                            }
                        }
                    }
                }
            }
            else if (needArgFor != null) {
                needArgFor.addArgument(arg);
                needArgFor = null;
            } else {
                parsedCommandLine.addExtraValue(arg);
            }
        }
        if (needArgFor != null) {
            needArgFor.argumentMissing();
        }
        return parsedCommandLine;
    }

    private OptionParseState addOption(ParsedCommandLine parsedCommandLine, String fullArg, String option) {
        ParsedCommandLineOption parsedOption = parsedCommandLine.addOption(option);
        String fullOptionStr = fullArg.startsWith("--") ? "--" + option : "-" + option;
        if (parsedOption == null) {
            throw new CommandLineArgumentException(String.format("Unknown command-line option '%s'.", fullOptionStr));
        }
        return new OptionParseState(parsedOption, fullOptionStr);
    }

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

    private static class OptionParseState {
        private final ParsedCommandLineOption option;
        private final String actualArg;

        private OptionParseState(ParsedCommandLineOption option, String actualArg) {
            this.option = option;
            this.actualArg = actualArg;
        }

        public void addArgument(String argument) {
            if (!getHasArgument()) {
                throw new CommandLineArgumentException(String.format("Command-line option '%s' does not take an argument.", actualArg));
            }
            if (argument.length() == 0) {
                throw new CommandLineArgumentException(String.format("An empty argument was provided for command-line option '%s'.", actualArg));
            }
            if (!option.getValues().isEmpty() && !option.getOption().getAllowsMultipleArguments()) {
                throw new CommandLineArgumentException(String.format("Multiple arguments were provided for command-line option '%s'.", actualArg));
            }
            option.addArgument(argument);
        }

        public boolean getHasArgument() {
            return option.getOption().getAllowsArguments();
        }

        public OptionParseState asNextArg() {
            return getHasArgument() ? this : null;
        }

        public void argumentMissing() {
            if (!getHasArgument()) {
                return;
            }
            throw new CommandLineArgumentException(String.format("No argument was provided for command-line option '%s'.", actualArg));
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
