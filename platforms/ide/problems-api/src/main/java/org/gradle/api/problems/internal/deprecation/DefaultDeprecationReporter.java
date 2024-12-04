/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.internal.deprecation;

import org.gradle.api.Action;
import org.gradle.api.problems.deprecation.DeprecateBehaviorSpec;
import org.gradle.api.problems.deprecation.DeprecateGenericSpec;
import org.gradle.api.problems.deprecation.DeprecationReporter;
import org.gradle.api.problems.internal.DefaultProblemBuilder;
import org.gradle.api.problems.internal.DefaultProblemReporter;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.Problem;

public class DefaultDeprecationReporter implements DeprecationReporter {

    private final DefaultProblemReporter reporter;

    public DefaultDeprecationReporter(DefaultProblemReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public Problem deprecate(String label, Action<DeprecateGenericSpec> feature) {
        DefaultProblemBuilder builder = reporter.createProblemBuilder();
        builder.id("generic", "Generic deprecation", GradleCoreProblemGroup.deprecation());
        builder.contextualLabel(label);
        feature.execute(new DefaultDeprecateGenericBuilder(builder));
        Problem problem = builder.build();
        reporter.report(problem);
        return problem;
    }

    @Override
    public Problem deprecateBehavior(String label, Action<DeprecateBehaviorSpec> feature) {
        DefaultProblemBuilder builder = reporter.createProblemBuilder();
        builder.id("behavior", "Behavior deprecation", GradleCoreProblemGroup.deprecation());
        builder.contextualLabel(label);
        feature.execute(new DefaultDeprecateBehaviorBuilder(builder));
        Problem problem = builder.build();
        reporter.report(problem);
        return problem;
    }

}
