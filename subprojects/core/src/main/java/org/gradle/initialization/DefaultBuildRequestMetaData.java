/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.util.Clock;

public class DefaultBuildRequestMetaData implements BuildRequestMetaData {
    private final BuildClientMetaData clientMetaData;
    private final Clock clock;

    public DefaultBuildRequestMetaData(BuildClientMetaData clientMetaData, long startTime) {
        this.clientMetaData = clientMetaData;
        clock = new Clock(startTime);
    }

    public DefaultBuildRequestMetaData(long startTime) {
        this(new GradleLauncherMetaData(), startTime);
    }

    public DefaultBuildRequestMetaData(BuildClientMetaData buildClientMetaData) {
        this.clientMetaData = buildClientMetaData;
        this.clock = new Clock();
    }

    public BuildClientMetaData getClient() {
        return clientMetaData;
    }

    public Clock getBuildTimeClock() {
        return clock;
    }
}
