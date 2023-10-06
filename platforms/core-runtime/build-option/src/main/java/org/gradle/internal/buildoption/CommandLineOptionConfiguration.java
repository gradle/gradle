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

package org.gradle.internal.buildoption;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for a command line option.
 *
 * @since 4.3
 */
public class CommandLineOptionConfiguration {
    private final String longOption;
    private final String shortOption;
    private final String description;
    private boolean incubating;
    private boolean deprecated;

    CommandLineOptionConfiguration(String longOption, String description) {
        this(longOption, null, description);
    }

    CommandLineOptionConfiguration(String longOption, @Nullable String shortOption, String description) {
        assert longOption != null : "longOption cannot be null";
        assert description != null : "description cannot be null";
        this.longOption = longOption;
        this.shortOption = shortOption;
        this.description = description;
    }

    public static CommandLineOptionConfiguration create(String longOption, String description) {
        return new CommandLineOptionConfiguration(longOption, description);
    }

    public static CommandLineOptionConfiguration create(String longOption, String shortOption, String description) {
        return new CommandLineOptionConfiguration(longOption, shortOption, description);
    }

    public CommandLineOptionConfiguration incubating() {
        incubating = true;
        return this;
    }

    public CommandLineOptionConfiguration deprecated() {
        deprecated = true;
        return this;
    }

    public String getLongOption() {
        return longOption;
    }

    @Nullable
    public String getShortOption() {
        return shortOption;
    }

    public String[] getAllOptions() {
        List<String> allOptions = new ArrayList<String>();
        allOptions.add(longOption);

        if (shortOption != null) {
            allOptions.add(shortOption);
        }

        return allOptions.toArray(new String[allOptions.size()]);
    }

    public String getDescription() {
        return description;
    }

    public boolean isIncubating() {
        return incubating;
    }

    public boolean isDeprecated() {
        return deprecated;
    }
}
