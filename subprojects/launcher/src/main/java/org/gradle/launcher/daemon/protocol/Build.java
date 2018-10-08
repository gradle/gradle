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
package org.gradle.launcher.daemon.protocol;

import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.BuildActionParameters;

import java.util.UUID;

public class Build extends Command {
    private final BuildAction action;
    private final BuildClientMetaData buildClientMetaData;
    private final long startTime;
    private final boolean interactive;
    private final BuildActionParameters parameters;

    public Build(UUID identifier, byte[] token, BuildAction action, BuildClientMetaData buildClientMetaData, long startTime, boolean interactive, BuildActionParameters parameters) {
        super(identifier, token);
        this.action = action;
        this.buildClientMetaData = buildClientMetaData;
        this.startTime = startTime;
        this.interactive = interactive;
        this.parameters = parameters;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isInteractive() {
        return interactive;
    }

    public BuildClientMetaData getBuildClientMetaData() {
        return buildClientMetaData;
    }

    public BuildRequestMetaData getBuildRequestMetaData() {
        return new DefaultBuildRequestMetaData(buildClientMetaData, startTime, interactive);
    }

    public BuildAction getAction() {
        return action;
    }

    public BuildActionParameters getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{"
            + "id=" + getIdentifier()
            + ", currentDir=" + parameters.getCurrentDir()
            + '}';
    }
}
