/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.problems.internal.emitters;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.ProblemEmitter;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.problems.ProblemUtils;
import org.gradle.problems.internal.rendering.ProblemRendererFactory;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class ConsoleProblemEmitter implements ProblemEmitter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleProblemEmitter.class);
    private final WarningMode warningMode;

    public ConsoleProblemEmitter(StartParameterInternal startParameter) {
        warningMode = startParameter.getWarningMode();
    }

    @Override
    public void emit(InternalProblem problem, @Nullable OperationIdentifier id) {
        if (shouldRender(problem)) {
            render(problem);
        }
    }

    private boolean shouldRender(InternalProblem problem) {
        // only render problem reports if warning mode is set to 'all'
        if (warningMode != WarningMode.All) {
            return false;
        }

        // Don't render deprecation warnings
        List<ProblemGroup> groups = ProblemUtils.groups(problem);
        ProblemGroup baseGroup = groups.get(0);
        if (GradleCoreProblemGroup.deprecation().equals(baseGroup)) {
            return false;
        }

        // Don't render java compilation warnings
        if (groups.size() > 1) {
            ProblemGroup subGroup = groups.get(1);
            if (GradleCoreProblemGroup.compilation().java().equals(subGroup)) {
                return false;
            }
        }
        return true;
    }

    private static void render(InternalProblem problem) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ProblemRendererFactory.standaloneProblemRenderer(pw).render(problem);
        String result = sw.toString();
        LOGGER.warn(result);
    }
}
