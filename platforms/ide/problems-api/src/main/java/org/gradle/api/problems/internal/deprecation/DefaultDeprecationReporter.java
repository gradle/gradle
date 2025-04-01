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

package org.gradle.api.problems.internal.deprecation;

import org.gradle.api.Action;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.deprecation.DeprecateSpec;
import org.gradle.api.problems.deprecation.DeprecationReporter;
import org.gradle.api.problems.deprecation.source.ReportSource;
import org.gradle.api.problems.internal.DefaultProblemReporter;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;

public class DefaultDeprecationReporter implements DeprecationReporter {

    private final DefaultProblemReporter reporter;

    public DefaultDeprecationReporter(DefaultProblemReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public void deprecate(ReportSource reportSource, String label, Action<DeprecateSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reportSource, reporter.createProblemBuilder());
        deprecationBuilder.getProblemBuilder()
            .id(GradleCoreProblemGroup.deprecation().generic())
            .contextualLabel(label)
            .stackLocation()
            .withException(new RuntimeException());
        spec.execute(deprecationBuilder);
        reportBuiltProblem(deprecationBuilder);
    }

    @Override
    public void deprecateMethod(ReportSource reportSource, Class<?> containingClass, String signature, Action<DeprecateSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reportSource, reporter.createProblemBuilder());
        String name = containingClass.getName() + "." + signature;
        deprecationBuilder.getProblemBuilder()
            .id(GradleCoreProblemGroup.deprecation().method())
            .contextualLabel(String.format("Method '%s' is deprecated", name))
            .stackLocation()
            .withException(new RuntimeException());

        spec.execute(deprecationBuilder);
        reportBuiltProblem(deprecationBuilder);
    }

    @Override
    public void deprecatePlugin(ReportSource reportSource, String pluginId, Action<DeprecateSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reportSource, reporter.createProblemBuilder());
        deprecationBuilder.getProblemBuilder()
            .id(GradleCoreProblemGroup.deprecation().plugin())
            .contextualLabel(String.format("Plugin '%s' is deprecated", pluginId))
            .stackLocation()
            .withException(new RuntimeException());
        spec.execute(deprecationBuilder);
        reportBuiltProblem(deprecationBuilder);
    }

    private Problem reportBuiltProblem(DefaultDeprecationBuilder builder) {
        InternalProblem deprecationProblem = builder.build();
        reporter.report(deprecationProblem);
        return deprecationProblem;
    }
}
