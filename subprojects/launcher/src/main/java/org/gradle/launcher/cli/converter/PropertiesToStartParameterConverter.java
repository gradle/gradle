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

import java.util.Map;

import static org.gradle.initialization.option.GradleBuildOptions.*;

public class PropertiesToStartParameterConverter {
    private final PropertiesToParallelismConfigurationConverter propertiesToParallelismConfigurationConverter = new PropertiesToParallelismConfigurationConverter();
    private final PropertiesToLogLevelConfigurationConverter propertiesToLogLevelConfigurationConverter = new PropertiesToLogLevelConfigurationConverter();

    public StartParameter convert(Map<String, String> properties, StartParameter startParameter) {
        startParameter.setConfigureOnDemand(isTrue(properties.get(CONFIGURE_ON_DEMAND.getGradleProperty())));

        propertiesToParallelismConfigurationConverter.convert(properties, startParameter);
        propertiesToLogLevelConfigurationConverter.convert(properties, startParameter);

        // If they use both, the newer property wins.
        String buildCacheEnabled = properties.get(BUILD_CACHE.getGradleProperty());
        if (buildCacheEnabled != null) {
            startParameter.setBuildCacheEnabled(isTrue(buildCacheEnabled));
        }

        return startParameter;
    }
}
