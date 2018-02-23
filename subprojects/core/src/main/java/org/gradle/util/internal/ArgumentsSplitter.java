/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.internal;

import java.util.ArrayList;
import java.util.List;

public class ArgumentsSplitter {

    /**
     * Splits the arguments string (for example, a program command line) into a collection.
     * Only supports space-delimited and/or quoted command line arguments. This currently does not handle escaping characters such as quotes.
     *
     * @param arguments the arguments, for example command line args.
     * @return separate command line arguments.
     */
    public static List<String> split(String arguments) {
        List<String> commandLineArguments = new ArrayList<String>();

        Character currentQuote = null;
        StringBuilder currentOption = new StringBuilder();
        boolean hasOption = false;

        for (int index = 0; index < arguments.length(); index++) {
            char c = arguments.charAt(index);
            if (currentQuote == null && Character.isWhitespace(c)) {
                if (hasOption) {
                    commandLineArguments.add(currentOption.toString());
                    hasOption = false;
                    currentOption.setLength(0);
                }
            } else if (currentQuote == null && (c == '"' || c == '\'')) {
                currentQuote = c;
                hasOption = true;
            } else if (currentQuote != null && c == currentQuote) {
                currentQuote = null;
            } else {
                currentOption.append(c);
                hasOption = true;
            }
        }

        if (hasOption) {
            commandLineArguments.add(currentOption.toString());
        }

        return commandLineArguments;
    }
}
