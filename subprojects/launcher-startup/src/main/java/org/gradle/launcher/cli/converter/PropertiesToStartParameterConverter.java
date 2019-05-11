/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.buildoption.BuildOption;

import java.util.List;
import java.util.Map;

public class PropertiesToStartParameterConverter {
    private final PropertiesToParallelismConfigurationConverter propertiesToParallelismConfigurationConverter = new PropertiesToParallelismConfigurationConverter();
    private final PropertiesToLogLevelConfigurationConverter propertiesToLogLevelConfigurationConverter = new PropertiesToLogLevelConfigurationConverter();
    private final List<BuildOption<StartParameterInternal>> buildOptions = StartParameterBuildOptions.get();

    public StartParameter convert(Map<String, String> properties, StartParameterInternal startParameter) {
        for (BuildOption<StartParameterInternal> option : buildOptions) {
            option.applyFromProperty(properties, startParameter);
        }

        propertiesToParallelismConfigurationConverter.convert(properties, startParameter);
        propertiesToLogLevelConfigurationConverter.convert(properties, startParameter);

        return startParameter;
    }
}
