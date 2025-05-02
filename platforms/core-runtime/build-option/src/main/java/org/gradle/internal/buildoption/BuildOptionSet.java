/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.cli.AbstractCommandLineConverter;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;

import java.util.List;
import java.util.Map;

public abstract class BuildOptionSet<T> {
    /**
     * Returns the options defined by this set.
     */
    abstract public List<? extends BuildOption<? super T>> getAllOptions();

    /**
     * Returns a {@link CommandLineConverter} that can parse the options defined by this set.
     */
    public CommandLineConverter<T> commandLineConverter() {
        return new AbstractCommandLineConverter<T>() {
            @Override
            public T convert(ParsedCommandLine args, T target) throws CommandLineArgumentException {
                for (BuildOption<? super T> option : getAllOptions()) {
                    option.applyFromCommandLine(args, target);
                }
                return target;
            }

            @Override
            public void configure(CommandLineParser parser) {
                for (BuildOption<? super T> option : getAllOptions()) {
                    option.configure(parser);
                }
            }
        };
    }

    /**
     * Returns a {@link PropertiesConverter} that can extract the options defined by this set.
     */
    public PropertiesConverter<T> propertiesConverter() {
        return new PropertiesConverter<T>() {
            @Override
            public T convert(Map<String, String> properties, T target) throws CommandLineArgumentException {
                for (BuildOption<? super T> option : getAllOptions()) {
                    option.applyFromProperty(properties, target);
                }
                return target;
            }
        };
    }
}
