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

import org.gradle.util.GUtil;

import java.util.*;

public class ParsedCommandLine {
    private final Map<String, ParsedCommandLineOption> optionsByString = new HashMap<String, ParsedCommandLineOption>();
    private final Set<String> presentOptions = new HashSet<String>();
    private final List<String> extraArguments = new ArrayList<String>();

    ParsedCommandLine(Iterable<CommandLineOption> options) {
        for (CommandLineOption option : options) {
            ParsedCommandLineOption parsedOption = new ParsedCommandLineOption(option);
            for (String optionStr : option.getOptions()) {
                optionsByString.put(optionStr, parsedOption);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("options: %s, extraArguments: %s", GUtil.toString(presentOptions), GUtil.toString(extraArguments));
    }

    public boolean hasOption(String option) {
        option(option);
        return presentOptions.contains(option);
    }
    
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

    ParsedCommandLineOption addOption(String option) {
        ParsedCommandLineOption parsedOption = optionsByString.get(option);
        if (parsedOption == null) {
            return null;
        }
        presentOptions.addAll(parsedOption.getOption().getOptions());
        return parsedOption;
    }
}
