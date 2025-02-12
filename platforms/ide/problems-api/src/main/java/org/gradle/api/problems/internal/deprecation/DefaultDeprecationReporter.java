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
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.deprecation.DeprecateGenericSpec;
import org.gradle.api.problems.deprecation.DeprecateMethodSpec;
import org.gradle.api.problems.deprecation.DeprecatePluginSpec;
import org.gradle.api.problems.deprecation.DeprecationReporter;
import org.gradle.api.problems.deprecation.ReportSource;
import org.gradle.api.problems.internal.DefaultProblemReporter;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.problems.internal.InternalProblem;

public class DefaultDeprecationReporter implements DeprecationReporter {

    private final DefaultProblemReporter reporter;

    public DefaultDeprecationReporter(DefaultProblemReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public Problem deprecate(ReportSource reportSource, String label, Action<DeprecateGenericSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reportSource, reporter.createProblemBuilder());
        deprecationBuilder.getProblemBuilder()
            .id("generic", "Generic deprecation", GradleCoreProblemGroup.deprecation().thisGroup())
            .contextualLabel(label)
            .stackLocation()
            .withException(new RuntimeException());
        spec.execute(deprecationBuilder);
        return reportBuiltProblem(deprecationBuilder);
    }

    @Override
    public Problem deprecateMethod(ReportSource reportSource, Class<?> containingClass, String signature, Action<DeprecateMethodSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reportSource, reporter.createProblemBuilder());
        String name = containingClass.getSimpleName() + "." + signature;
        deprecationBuilder.getProblemBuilder()
            .id(name, name, methodDeprecationProblemGroup(reportSource))
            .contextualLabel(String.format("Method '%s#%s' is deprecated", containingClass.getName(), signature))
            .stackLocation()
            .withException(new RuntimeException());
        spec.execute(deprecationBuilder);
        return reportBuiltProblem(deprecationBuilder);
    }

    ProblemGroup methodDeprecationProblemGroup(ReportSource reportSource) {
        if (ReportSource.gradle().equals(reportSource)) {
            return ProblemGroup.create(
                "method",
                "Method",
                ProblemGroup.create(
                    "gradle",
                    "Gradle",
                    GradleCoreProblemGroup.deprecation().thisGroup()
                )
            );
        } else if (reportSource instanceof ReportSource.PluginReportSource) {
            String id = ((ReportSource.PluginReportSource) reportSource).getId();
            return ProblemGroup.create(
                "method",
                "Method",
                ProblemGroup.create(
                    "plugin",
                    "Plugin",
                    ProblemGroup.create(
                        id,
                        id,
                        GradleCoreProblemGroup.deprecation().thisGroup()
                    )
                )
            );
        } else {
            throw new IllegalArgumentException("Unsupported report source: " + reportSource);
        }
    }

    @Override
    public Problem deprecatePlugin(ReportSource reportSource, String pluginId, Action<DeprecatePluginSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reportSource, reporter.createProblemBuilder());
        deprecationBuilder.getProblemBuilder()
            .id(pluginId, pluginId, GradleCoreProblemGroup.deprecation().plugin())
            .contextualLabel(String.format("Plugin '%s' is deprecated", pluginId))
            .stackLocation()
            .withException(new RuntimeException());
        spec.execute(deprecationBuilder);
        return reportBuiltProblem(deprecationBuilder);
    }

    private Problem reportBuiltProblem(DefaultDeprecationBuilder builder) {
        InternalProblem deprecationProblem = builder.build();
        reporter.report(deprecationProblem);
        return deprecationProblem;
    }

    // TODO (donat) cover indirectDeprecation: by default all deprecations are direct and so they should have stackLocation. The API should have an indirect() call on the spec to disable that.
}
