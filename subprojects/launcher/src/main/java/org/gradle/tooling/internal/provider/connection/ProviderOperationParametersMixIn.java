/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider.connection;

import org.gradle.api.logging.LogLevel;

public class ProviderOperationParametersMixIn {
    private final BuildLogLevelMixIn buildLogLevelMixIn;
    private final ColorOutputMixIn colorOutputMixIn;

    public ProviderOperationParametersMixIn(ProviderOperationParameters parameters) {
        buildLogLevelMixIn = new BuildLogLevelMixIn(parameters);
        colorOutputMixIn = new ColorOutputMixIn(parameters);
    }

    public LogLevel getBuildLogLevel() {
        return buildLogLevelMixIn.getBuildLogLevel();
    }

    public Boolean isColorOutput() {
        return colorOutputMixIn.isColorOutput();
    }
}
