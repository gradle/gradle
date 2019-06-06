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

package org.gradle.launcher.cli.converter;

import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.ParallelismBuildOptions;
import org.gradle.internal.buildoption.BuildOption;

import java.util.List;
import java.util.Map;

public class PropertiesToParallelismConfigurationConverter {

    private List<BuildOption<ParallelismConfiguration>> buildOptions = ParallelismBuildOptions.get();

    public ParallelismConfiguration convert(Map<String, String> properties, ParallelismConfiguration parallelismConfiguration) {
        for (BuildOption<ParallelismConfiguration> option : buildOptions) {
            option.applyFromProperty(properties, parallelismConfiguration);
        }

        return parallelismConfiguration;
    }
}
