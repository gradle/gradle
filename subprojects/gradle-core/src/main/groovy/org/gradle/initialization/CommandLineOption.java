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

import java.util.List;
import java.util.Set;

public class CommandLineOption {
    private final Set<String> options;
    private Class<?> argumentType = Void.TYPE;
    private String description;
    private String subcommand;

    public CommandLineOption(Iterable<String> options) {
        this.options = GUtil.addSets(options);
    }

    public Set<String> getOptions() {
        return options;
    }

    public CommandLineOption hasArgument() {
        argumentType = String.class;
        return this;
    }

    public CommandLineOption hasArguments() {
        argumentType = List.class;
        return this;
    }

    public String getSubcommand() {
        return subcommand;
    }

    public CommandLineOption mapsToSubcommand(String command) {
        this.subcommand = command;
        return this;
    }

    public String getDescription() {
        return description;
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
}
