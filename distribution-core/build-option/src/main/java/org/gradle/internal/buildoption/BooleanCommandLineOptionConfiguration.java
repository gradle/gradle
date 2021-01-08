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

/**
 * Configuration for a boolean command line option.
 *
 * @since 4.4
 */
public class BooleanCommandLineOptionConfiguration extends CommandLineOptionConfiguration {

    private final String disabledDescription;

    BooleanCommandLineOptionConfiguration(String longOption, String enabledDescription, String disabledDescription) {
        this(longOption, null, enabledDescription, disabledDescription);
    }

    BooleanCommandLineOptionConfiguration(String longOption, @Nullable String shortOption, String enabledDescription, String disabledDescription) {
        super(longOption, shortOption, enabledDescription);
        assert disabledDescription != null : "disabled description cannot be null";
        this.disabledDescription = disabledDescription;
    }

    public static BooleanCommandLineOptionConfiguration create(String longOption, String enabledDescription, String disabledDescription) {
        return new BooleanCommandLineOptionConfiguration(longOption, enabledDescription, disabledDescription);
    }

    public static BooleanCommandLineOptionConfiguration create(String longOption, String shortOption, String enabledDescription, String disabledDescription) {
        return new BooleanCommandLineOptionConfiguration(longOption, shortOption, enabledDescription, disabledDescription);
    }

    public String getDisabledDescription() {
        return disabledDescription;
    }

    @Override
    public BooleanCommandLineOptionConfiguration incubating() {
        super.incubating();
        return this;
    }

    @Override
    public BooleanCommandLineOptionConfiguration deprecated() {
        super.deprecated();
        return this;
    }
}
