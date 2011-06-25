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
package org.gradle.launcher;

import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.ParsedCommandLine;
import org.gradle.util.GUtil;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DaemonBuildAction implements Runnable {
    private final DaemonClient client;
    private final ParsedCommandLine args;
    private final File currentDir;
    private final BuildClientMetaData clientMetaData;
    private final long startTime;
    private final Map<String, String> systemProperties;

    public DaemonBuildAction(DaemonClient client, ParsedCommandLine args, File currentDir, BuildClientMetaData clientMetaData, long startTime, Map<?, ?> systemProperties) {
        this.client = client;
        this.args = args;
        this.currentDir = currentDir;
        this.clientMetaData = clientMetaData;
        this.startTime = startTime;
        this.systemProperties = new HashMap<String, String>();
        GUtil.addToMap(this.systemProperties, systemProperties);
    }

    public void run() {
        client.execute(new ExecuteBuildAction(currentDir, args), new DefaultBuildActionParameters(clientMetaData, startTime, systemProperties));
    }
}
