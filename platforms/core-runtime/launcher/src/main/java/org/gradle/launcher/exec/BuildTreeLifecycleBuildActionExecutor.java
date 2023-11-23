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

package org.gradle.launcher.exec;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.BuildLayoutValidator;
import org.gradle.internal.buildtree.BuildActionModelRequirements;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeModelControllerServices;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.buildtree.RunTasksRequirements;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.session.BuildSessionActionExecutor;
import org.gradle.internal.session.BuildSessionContext;
import org.gradle.tooling.internal.provider.action.BuildModelAction;
import org.gradle.tooling.internal.provider.action.ClientProvidedBuildAction;
import org.gradle.tooling.internal.provider.action.ClientProvidedPhasedAction;

/**
 * A {@link BuildActionExecuter} responsible for establishing the build tree for a single invocation of a {@link BuildAction}.
 */
public class BuildTreeLifecycleBuildActionExecutor implements BuildSessionActionExecutor {
    private final BuildTreeModelControllerServices buildTreeModelControllerServices;
    private final BuildLayoutValidator buildLayoutValidator;

    public BuildTreeLifecycleBuildActionExecutor(
        BuildTreeModelControllerServices buildTreeModelControllerServices,
        BuildLayoutValidator buildLayoutValidator
    ) {
        this.buildTreeModelControllerServices = buildTreeModelControllerServices;
        this.buildLayoutValidator = buildLayoutValidator;
    }

    @Override
    public BuildActionRunner.Result execute(BuildAction action, BuildSessionContext buildSession) {
        BuildActionRunner.Result result = null;
        try {
            buildLayoutValidator.validate(action.getStartParameter());

            BuildActionModelRequirements actionRequirements = buildActionModelRequirementsFor(action);
            BuildTreeModelControllerServices.Supplier modelServices = buildTreeModelControllerServices.servicesForBuildTree(actionRequirements);
            BuildInvocationScopeId buildInvocationScopeId = new BuildInvocationScopeId(UniqueId.generate());
            BuildTreeState buildTree = new BuildTreeState(buildInvocationScopeId, buildSession.getServices(), modelServices);
            try {
                result = buildTree.run(context -> context.execute(action));
            } finally {
                buildTree.close();
            }
        } catch (Throwable t) {
            if (result == null) {
                // Did not create a result
                // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
                // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
                throw UncheckedException.throwAsUncheckedException(t);
            } else {
                // Cleanup has failed, combine the cleanup failure with other failures that may be packed in the result
                // Note: throw the failure rather than returning a result object containing the failure, as console failure logging based on the _result_ happens down in the root build scope
                // whereas console failure logging based on the _thrown exception_ happens up outside session scope. It would be better to refactor so that a result can be returned from here
                throw UncheckedException.throwAsUncheckedException(result.addFailure(t).getBuildFailure());
            }
        }
        return result;
    }

    private static BuildActionModelRequirements buildActionModelRequirementsFor(BuildAction action) {
        if (action instanceof BuildModelAction && action.isCreateModel()) {
            BuildModelAction buildModelAction = (BuildModelAction) action;
            return new QueryModelRequirements(action.getStartParameter(), action.isRunTasks(), buildModelAction.getModelName());
        } else if (action instanceof ClientProvidedBuildAction) {
            return new RunActionRequirements(action.getStartParameter(), action.isRunTasks());
        } else if (action instanceof ClientProvidedPhasedAction) {
            return new RunPhasedActionRequirements(action.getStartParameter(), action.isRunTasks());
        } else {
            return new RunTasksRequirements(action.getStartParameter());
        }
    }
}
