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

package org.gradle.internal.scripts.operations;

import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.RunnableBuildOperation;

public class CompileScriptBuildOperation implements RunnableBuildOperation {
    private static final CompileScriptBuildOperationType.Result RESULT = new CompileScriptBuildOperationType.Result() {};
    private final Runnable compile;
    private final String displayName;
    private final String buildPath;
    private final String scriptPath;
    private final String language;
    private final String stage;

    public CompileScriptBuildOperation(Runnable compile, String displayName, String buildPath, String scriptPath, String language, String stage) {
        this.compile = compile;
        this.displayName = displayName;
        this.buildPath = buildPath;
        this.scriptPath = scriptPath;
        this.language = language;
        this.stage = stage;
    }

    @Override
    public void run(BuildOperationContext context) {
        compile.run();
        context.setResult(RESULT);
    }

    @Override
    public BuildOperationDescriptor.Builder description() {
        return BuildOperationDescriptor.displayName(displayName).details(new CompileScriptBuildOperationType.Details() {
            @Override
            public String getBuildPath() {
                return buildPath;
            }

            @Override
            public String getLanguage() {
                return language;
            }

            @Override
            public String getStage() {
                return stage;
            }

            @Override
            public String getScriptPath() {
                return scriptPath;
            }
        });
    }
}
