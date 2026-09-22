/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction;
import org.jspecify.annotations.NullMarked;

/**
 * Agent mode only applies to builds run from the command line, whose client redirects the output before the build starts.
 * A Tooling API client owns the output streams and receives the build's outcome as structured events, so agent mode
 * would only hijack its streams. As the setting can also be inherited from properties and the environment, it is ignored
 * for such builds rather than honoured, and the client is told about it.
 */
@NullMarked
public class AgentModeIgnoringBuildActionRunner implements BuildActionRunner {
    private static final ProblemGroup PROBLEM_GROUP = ProblemGroup.create("agent-mode", "Agent mode");
    private static final ProblemId PROBLEM_ID = ProblemId.create("ignored-for-tooling-api", "Agent mode ignored for Tooling API build", PROBLEM_GROUP);

    private final ProblemsInternal problems;
    private final BuildActionRunner delegate;

    public AgentModeIgnoringBuildActionRunner(ProblemsInternal problems, BuildActionRunner delegate) {
        this.problems = problems;
        this.delegate = delegate;
    }

    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        if (action.getStartParameter().isAgentMode() && !(action instanceof ExecuteBuildAction)) {
            problems.getInternalReporter().report(PROBLEM_ID, spec -> spec
                .contextualLabel("Agent mode has been ignored because the build was run through the Tooling API.")
                .details("Agent mode only applies to builds run from the command line. It has no effect on a build run through the Tooling API, which receives the build's outcome as structured events.")
                .solution("Do not request agent mode for builds run through the Tooling API, for example by not setting the org.gradle.agent Gradle property globally.")
            );
        }
        return delegate.run(action, buildController);
    }
}
