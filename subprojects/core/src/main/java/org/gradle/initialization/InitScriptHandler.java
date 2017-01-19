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

import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.configuration.InitScriptProcessor;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;

import java.io.File;
import java.net.URI;
import java.util.List;

/**
 * Finds and executes all init scripts for a given build.
 */
public class InitScriptHandler {
    private final InitScriptProcessor processor;
    private final BuildOperationExecutor buildOperationExecutor;
    private final FileResolver resolver;

    public InitScriptHandler(InitScriptProcessor processor, BuildOperationExecutor buildOperationExecutor, FileResolver resolver) {
        this.processor = processor;
        this.buildOperationExecutor = buildOperationExecutor;
        this.resolver = resolver;
    }

    public void executeScripts(final GradleInternal gradle) {
        final StartParameter startParameter = gradle.getStartParameter();
        final List<Object> initScripts = startParameter.getAllInitScripts();
        if (initScripts.isEmpty()) {
            return;
        }

        BuildOperationDetails operationDetails = BuildOperationDetails.displayName("Run init scripts").progressDisplayName("init scripts").build();
        if (initScripts.isEmpty()) {
            buildOperationExecutor.run(operationDetails, new Action<BuildOperationContext>() {
                @Override
                public void execute(BuildOperationContext buildOperationContext) {
                    for (Object script : initScripts) {
                        URI scriptUri = resolver.resolveUri(script);
                        processor.process(new UriScriptSource("initialization script", scriptUri), gradle);
                    }
                }
            });
        }
    }
}

