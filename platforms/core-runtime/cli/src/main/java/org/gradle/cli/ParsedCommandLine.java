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

import java.util.*;

public class ParsedCommandLine {
    private final Map<String, ParsedCommandLineOption> optionsByString = new HashMap<String, ParsedCommandLineOption>();
    private final Set<String> presentOptions = new HashSet<String>();
    private final Set<String> removedOptions = new HashSet<String>();
    private final List<String> extraArguments = new ArrayList<String>();

    ParsedCommandLine(Iterable<CommandLineOption> options) {
        for (CommandLineOption option : options) {
            ParsedCommandLineOption parsedOption = new ParsedCommandLineOption();
            for (String optionStr : option.getOptions()) {
                optionsByString.put(optionStr, parsedOption);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("options: %s, extraArguments: %s, removedOptions: %s", quoteAndJoin(presentOptions), quoteAndJoin(extraArguments), quoteAndJoin(removedOptions));
    }

    private String quoteAndJoin(Iterable<String> strings) {
        StringBuilder output = new StringBuilder();
        boolean isFirst = true;
        for (String string : strings) {
            if (!isFirst) {
                output.append(", ");
            }
            output.append("'");
            output.append(string);
            output.append("'");
            isFirst = false;
        }
        return output.toString();
    }

    /**
     * Returns true if the given option is present in this command-line.
     *
     * @param option The option, without the '-' or '--' prefix.
     * @return true if the option is present.
     */
    public boolean hasOption(String option) {
        option(option);
        return presentOptions.contains(option);
    }

    /**
     * Returns true if the given option was present in this command-line,
     * but was removed because another option appeared later that replaces it.
     *
     * @param option The option, without the '-' or '--' prefix.
     * @return true if the option was present.
     */
    public boolean hadOptionRemoved(String option) {
        option(option);
        return removedOptions.contains(option);
    }

    /**
     * See also {@link #hasOption}.
     *
     * @param logLevelOptions the options to check
     * @return true if any of the passed options is present
     */
    public boolean hasAnyOption(Collection<String> logLevelOptions) {
        for (String option : logLevelOptions) {
            if (hasOption(option)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value of the given option.
     *
     * @param option The option, without the '-' or '--' prefix.
     * @return The option. never returns null.
     */
    public ParsedCommandLineOption option(String option) {
        ParsedCommandLineOption parsedOption = optionsByString.get(option);
        if (parsedOption == null) {
            throw new IllegalArgumentException(String.format("Option '%s' not defined.", option));
        }
        return parsedOption;
    }

    public List<String> getExtraArguments() {
        return extraArguments;
    }

    void addExtraValue(String value) {
        extraArguments.add(value);
    }

    ParsedCommandLineOption addOption(String optionStr, CommandLineOption option) {
        ParsedCommandLineOption parsedOption = optionsByString.get(optionStr);
        presentOptions.addAll(option.getOptions());
        return parsedOption;
    }

    void removeOption(CommandLineOption option) {
        for (String optionStr : option.getOptions()) {
            if (presentOptions.remove(optionStr)) {
                // Only keep track of removed options that were present in the command line
                removedOptions.add(optionStr);
            }
        }
    }
}
