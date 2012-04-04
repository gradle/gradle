/*
 * Copyright 2011 the original author or authors.
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
package org.gradle;

import org.gradle.cli.CommandLineArgumentException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The options supplied by a user to refresh dependencies and other external resources.
 * @deprecated Use {@link StartParameter#setRefreshDependencies(boolean)} instead.
 */
@Deprecated
public class RefreshOptions implements Serializable {
    public static final RefreshOptions NONE = new RefreshOptions(Collections.<Option>emptyList());

    private final List<Option> options;

    public RefreshOptions(List<Option> options) {
        this.options = options;
    }

    public static RefreshOptions fromCommandLineOptions(List<String> optionNames) {
        if (optionNames.size() == 0) {
            return new RefreshOptions(Arrays.asList(Option.values()));
        } 
        
        List<Option> options = new ArrayList<Option>();
        for (String optionName : optionNames) {
            try {
                options.add(Option.valueOf(optionName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new CommandLineArgumentException(String.format("Unknown refresh option '%s' specified.", optionName));
            }
        }
        return new RefreshOptions(options);
    }

    public boolean refreshDependencies() {
        return options.contains(Option.DEPENDENCIES);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RefreshOptions that = (RefreshOptions) o;
        return options.equals(that.options);

    }

    @Override
    public int hashCode() {
        return options.hashCode();
    }

    /**
     * The set of allowable options.
     */
    public enum Option {
        DEPENDENCIES
    }
}
