/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.Factory;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.util.StdinSwapper;

import java.io.InputStream;

class StdInSwapExecuter implements BuildActionExecuter<BuildActionParameters, BuildRequestContext> {
    private final InputStream standardInput;
    private final BuildActionExecuter<BuildActionParameters, BuildRequestContext> embeddedExecutor;

    public StdInSwapExecuter(InputStream standardInput, BuildActionExecuter<BuildActionParameters, BuildRequestContext> embeddedExecutor) {
        this.standardInput = standardInput;
        this.embeddedExecutor = embeddedExecutor;
    }

    @Override
    public BuildActionResult execute(final BuildAction action, final BuildActionParameters actionParameters, final BuildRequestContext requestContext) {
        return new StdinSwapper().swap(standardInput, new Factory<BuildActionResult>() {
            @Override
            public BuildActionResult create() {
                return embeddedExecutor.execute(action, actionParameters, requestContext);
            }
        });
    }
}
