/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.events.configuration.internal;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.configuration.ProjectConfigurationFailureResult;
import org.gradle.tooling.events.internal.DefaultOperationFailureResult;

import java.util.List;

public class DefaultProjectConfigurationFailureResult extends DefaultOperationFailureResult implements ProjectConfigurationFailureResult {

    private final List<? extends PluginApplicationResult> pluginApplicationResults;

    public DefaultProjectConfigurationFailureResult(long startTime, long endTime, List<? extends Failure> failures, List<? extends PluginApplicationResult> pluginApplicationResults) {
        super(startTime, endTime, failures);
        this.pluginApplicationResults = pluginApplicationResults;
    }

    @Override
    public List<? extends PluginApplicationResult> getPluginApplicationResults() {
        return pluginApplicationResults;
    }

}
