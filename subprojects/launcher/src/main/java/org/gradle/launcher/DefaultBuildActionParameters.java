/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.util.GUtil;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DefaultBuildActionParameters implements BuildActionParameters, Serializable {
    private final BuildClientMetaData clientMetaData;
    private final long startTime;
    private final Map<String, String> systemProperties;

    public DefaultBuildActionParameters(BuildClientMetaData clientMetaData, long startTime, Map<?, ?> systemProperties) {
        this.clientMetaData = clientMetaData;
        this.startTime = startTime;
        this.systemProperties = new HashMap<String, String>();
        GUtil.addToMap(this.systemProperties, systemProperties);
    }

    public BuildRequestMetaData getBuildRequestMetaData() {
        return new DefaultBuildRequestMetaData(clientMetaData, startTime);
    }

    public BuildClientMetaData getClientMetaData() {
        return clientMetaData;
    }

    public Map<String, String> getSystemProperties() {
        return systemProperties;
    }
}
