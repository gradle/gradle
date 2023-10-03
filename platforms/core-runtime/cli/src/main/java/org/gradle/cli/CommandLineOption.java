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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CommandLineOption {
    private final Set<String> options = new HashSet<String>();
    private Class<?> argumentType = Void.TYPE;
    private String description;
    private boolean incubating;
    private final Set<CommandLineOption> groupWith = new HashSet<CommandLineOption>();
    private boolean deprecated;

    public CommandLineOption(Iterable<String> options) {
        for (String option : options) {
            this.options.add(option);
        }
    }

    public Set<String> getOptions() {
        return options;
    }

    public CommandLineOption hasArgument(Class<?> argumentType) {
        this.argumentType = argumentType;
        return this;
    }

    public CommandLineOption hasArgument() {
        this.argumentType = String.class;
        return this;
    }

    public CommandLineOption hasArguments() {
        argumentType = List.class;
        return this;
    }

    public String getDescription() {
        StringBuilder result = new StringBuilder();
        if (description != null) {
            result.append(description);
        }

        appendMessage(result, deprecated, "[deprecated]");
        appendMessage(result, incubating, "[incubating]");

        return result.toString();
    }

    private void appendMessage(StringBuilder result, boolean append, String message) {
        if (append) {
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(message);
        }
    }

    public CommandLineOption hasDescription(String description) {
        this.description = description;
        return this;
    }

    public boolean getAllowsArguments() {
        return argumentType != Void.TYPE;
    }

    public boolean getAllowsMultipleArguments() {
        return argumentType == List.class;
    }

    public CommandLineOption deprecated() {
        this.deprecated = true;
        return this;
    }

    public CommandLineOption incubating() {
        incubating = true;
        return this;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public boolean isIncubating() {
        return incubating;
    }

    Set<CommandLineOption> getGroupWith() {
        return groupWith;
    }

    void groupWith(Set<CommandLineOption> options) {
        this.groupWith.addAll(options);
        this.groupWith.remove(this);
    }
}
