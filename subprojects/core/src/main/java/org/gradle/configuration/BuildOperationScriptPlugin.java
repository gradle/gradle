/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.configuration;

import org.gradle.api.Action;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationExecutor;

/**
 * A decorating {@link ScriptPlugin} implementation that delegates to a given
 * delegatee implementation, but wraps the apply() execution in a
 * {@link org.gradle.internal.operations.BuildOperation}.
 */
public class BuildOperationScriptPlugin implements ScriptPlugin {

    private ScriptPlugin decorated;
    private BuildOperationExecutor buildOperationExecutor;

    public BuildOperationScriptPlugin(ScriptPlugin decorated, BuildOperationExecutor buildOperationExecutor) {
        this.decorated = decorated;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public ScriptSource getSource() {
        return decorated.getSource();
    }

    @Override
    public void apply(final Object target) {
        String operationDisplayNamePrefix = "Apply " + getSource().getDisplayName() + " to " + target;
        buildOperationExecutor.run(operationDisplayNamePrefix, new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                decorated.apply(target);
            }
        });
    }
}
