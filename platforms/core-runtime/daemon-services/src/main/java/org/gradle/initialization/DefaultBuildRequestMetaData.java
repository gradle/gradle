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

import org.gradle.configuration.DefaultBuildClientMetaData;
import org.gradle.configuration.GradleLauncherMetaData;

public class DefaultBuildRequestMetaData implements BuildRequestMetaData {
    private final BuildClientMetaData clientMetaData;
    private final long startTime;
    private final boolean interactive;

    public DefaultBuildRequestMetaData(BuildClientMetaData clientMetaData, long startTime, boolean interactive) {
        this.clientMetaData = clientMetaData;
        this.startTime = startTime;
        this.interactive = interactive;
    }

    public DefaultBuildRequestMetaData(long startTime) {
        this(startTime, false);
    }

    public DefaultBuildRequestMetaData(long startTime, boolean interactive) {
        this(new DefaultBuildClientMetaData(new GradleLauncherMetaData()), startTime, interactive);
    }

    @Override
    public BuildClientMetaData getClient() {
        return clientMetaData;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public boolean isInteractive() {
        return interactive;
    }
}
