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

package org.gradle.initialization;

import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.Factory;
import org.gradle.internal.buildoption.BooleanBuildOption;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.CommandLineOptionConfiguration;
import org.gradle.internal.buildoption.StringBuildOption;

import java.util.ArrayList;
import java.util.List;

public class ParallelismBuildOptionFactory implements Factory<List<BuildOption<ParallelismConfiguration>>> {

    private final List<BuildOption<ParallelismConfiguration>> options = new ArrayList<BuildOption<ParallelismConfiguration>>();

    public ParallelismBuildOptionFactory() {
        options.add(new ParallelOption());
        options.add(new MaxWorkersOption());
    }

    @Override
    public List<BuildOption<ParallelismConfiguration>> create() {
        return options;
    }

    public static class ParallelOption extends BooleanBuildOption<ParallelismConfiguration> {
        public static final String GRADLE_PROPERTY = "org.gradle.parallel";

        public ParallelOption() {
            super(GRADLE_PROPERTY, CommandLineOptionConfiguration.create("parallel", "Build projects in parallel. Gradle will attempt to determine the optimal number of executor threads to use.").incubating());
        }

        @Override
        public void applyTo(boolean value, ParallelismConfiguration settings) {
            settings.setParallelProjectExecutionEnabled(value);
        }
    }

    public static class MaxWorkersOption extends StringBuildOption<ParallelismConfiguration> {
        public static final String GRADLE_PROPERTY = "org.gradle.workers.max";

        public MaxWorkersOption() {
            super(GRADLE_PROPERTY, CommandLineOptionConfiguration.create("max-workers", "Configure the number of concurrent workers Gradle is allowed to use.").incubating());
        }

        @Override
        public void applyTo(String value, ParallelismConfiguration settings) {
            try {
                int workerCount = Integer.parseInt(value);
                if (workerCount < 1) {
                    invalidMaxWorkersSwitchValue(value);
                }
                settings.setMaxWorkerCount(workerCount);
            } catch (NumberFormatException e) {
                invalidMaxWorkersSwitchValue(value);
            }
        }

        private void invalidMaxWorkersSwitchValue(String value) {
            throw new IllegalArgumentException(String.format("Argument value '%s' given for system property %s or --%s option is invalid (must be a positive, non-zero, integer)", value, gradleProperty, commandLineOptionConfiguration.getLongOption()));
        }
    }
}
