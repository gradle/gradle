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

import org.gradle.api.internal.GradleInternal;
import org.gradle.configuration.InitScriptProcessor;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.internal.resource.TextResource;

import java.io.File;
import java.util.List;

/**
 * Finds and executes all init scripts for a given build.
 */
public class InitScriptHandler {
    private final InitScriptProcessor processor;
    private final BuildOperationExecutor buildOperationExecutor;
    private final TextFileResourceLoader resourceLoader;

    public InitScriptHandler(InitScriptProcessor processor, BuildOperationExecutor buildOperationExecutor, TextFileResourceLoader resourceLoader) {
        this.processor = processor;
        this.buildOperationExecutor = buildOperationExecutor;
        this.resourceLoader = resourceLoader;
    }

    public void executeScripts(final GradleInternal gradle) {
        final List<File> initScripts = gradle.getStartParameter().getAllInitScripts();
        if (initScripts.isEmpty()) {
            return;
        }

        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                for (File script : initScripts) {
                    TextResource resource = resourceLoader.loadFile("initialization script", script);
                    processor.process(new TextResourceScriptSource(resource), gradle);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Run init scripts").progressDisplayName("Running init scripts");
            }
        });
    }
}

