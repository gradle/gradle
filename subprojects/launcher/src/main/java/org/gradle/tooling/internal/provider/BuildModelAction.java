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
package org.gradle.tooling.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.*;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

public class BuildModelAction implements BuildAction<BuildActionResult>, Serializable {
    private final boolean runTasks;
    private final String modelName;

    public BuildModelAction(String modelName, boolean runTasks) {
        this.modelName = modelName;
        this.runTasks = runTasks;
    }

    public BuildActionResult run(BuildController buildController) {
        DefaultGradleLauncher launcher = (DefaultGradleLauncher) buildController.getLauncher();
        final PayloadSerializer payloadSerializer = launcher.getGradle().getServices().get(PayloadSerializer.class);

        // The following is all very awkward because the contract for BuildController is still just a
        // rough wrapper around GradleLauncher, which means we can only get at the model and various
        // services by using listeners.

        final AtomicReference<Object> model = new AtomicReference<Object>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<RuntimeException>();
        final Action<GradleInternal> action = new Action<GradleInternal>() {
            public void execute(GradleInternal gradle) {
                ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
                ToolingModelBuilder builder;
                try {
                    builder = builderRegistry.getBuilder(modelName);
                } catch (UnknownModelException e) {
                    failure.set((InternalUnsupportedModelException) (new InternalUnsupportedModelException().initCause(e)));
                    return;
                }
                Object result;
                if (builder instanceof ProjectSensitiveToolingModelBuilder) {
                    result = ((ProjectSensitiveToolingModelBuilder) builder).buildAll(modelName, gradle.getDefaultProject(), true);
                } else {
                    result = builder.buildAll(modelName, gradle.getDefaultProject());
                }
                model.set(result);
            }
        };

        if (runTasks) {
            launcher.addListener(new TasksCompletionListener() {
                public void onTasksFinished(GradleInternal gradle) {
                    action.execute(gradle);
                }
            });
            buildController.run();
        } else {
            launcher.addListener(new ModelConfigurationListener() {
                public void onConfigure(GradleInternal gradle) {
                    // Currently need to force everything to be configured
                    ensureAllProjectsEvaluated(gradle);
                    action.execute(gradle);
                }
            });
            buildController.configure();
        }

        if (failure.get() != null) {
            throw failure.get();
        }
        return new BuildActionResult(payloadSerializer.serialize(model.get()), null);
    }

    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }

    private void ensureAllProjectsEvaluated(GradleInternal gradle) {
        gradle.getRootProject().allprojects((Action) new Action<ProjectInternal>() {
            public void execute(ProjectInternal projectInternal) {
                projectInternal.evaluate();
            }
        });
    }
}
