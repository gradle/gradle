/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.internal.GradleInternal;
import org.gradle.configuration.InitScriptProcessor;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;

import java.io.File;
import java.util.List;

/**
 * Finds and executes all init scripts for a given build.
 */
public class InitScriptHandler {
    private final InitScriptProcessor processor;
    private final BuildOperationExecutor buildOperationExecutor;

    public InitScriptHandler(InitScriptProcessor processor, BuildOperationExecutor buildOperationExecutor) {
        this.processor = processor;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public void executeScripts(final GradleInternal gradle) {
        final List<File> initScripts = gradle.getStartParameter().getAllInitScripts();
        if (initScripts.isEmpty()) {
            return;
        }

        BuildOperationDetails operationDetails = BuildOperationDetails.displayName("Run init scripts").progressDisplayName("init scripts").build();
        buildOperationExecutor.run(operationDetails, new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                for (File script : initScripts) {
                    processor.process(new UriScriptSource("initialization script", script), gradle);
                }
            }
        });
    }
}

