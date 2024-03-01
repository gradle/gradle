/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.launcher.cli.converter;

import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.internal.buildoption.EnumBuildOption;
import org.gradle.internal.buildoption.Origin;

import java.util.Collections;
import java.util.List;

public class WelcomeMessageBuildOptions extends BuildOptionSet<WelcomeMessageConfiguration> {

    private static List<BuildOption<WelcomeMessageConfiguration>> options = Collections.singletonList(new WelcomeMessageOption());

    @Override
    public List<? extends BuildOption<? super WelcomeMessageConfiguration>> getAllOptions() {
        return options;
    }

    public static class WelcomeMessageOption extends EnumBuildOption<WelcomeMessageDisplayMode, WelcomeMessageConfiguration> {

        public static final String PROPERTY_NAME = "org.gradle.welcome";

        public WelcomeMessageOption() {
            super(PROPERTY_NAME, WelcomeMessageDisplayMode.class, WelcomeMessageDisplayMode.values(), PROPERTY_NAME);
        }

        @Override
        public void applyTo(WelcomeMessageDisplayMode value, WelcomeMessageConfiguration settings, Origin origin) {
            settings.setWelcomeMessageDisplayMode(value);
        }
    }
}
