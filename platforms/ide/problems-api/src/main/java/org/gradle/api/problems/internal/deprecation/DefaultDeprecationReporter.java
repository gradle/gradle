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
import org.gradle.api.problems.deprecation.DeprecateGenericSpec;
import org.gradle.api.problems.deprecation.DeprecateMethodSpec;
import org.gradle.api.problems.deprecation.DeprecatePluginSpec;
import org.gradle.api.problems.deprecation.DeprecationReporter;
import org.gradle.api.problems.internal.DefaultProblemReporter;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;

public class DefaultDeprecationReporter implements DeprecationReporter {

    private final DefaultProblemReporter reporter;

    public DefaultDeprecationReporter(DefaultProblemReporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public Problem deprecate(String label, Action<DeprecateGenericSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reporter.createProblemBuilder());
        deprecationBuilder.getProblemBuilder()
            .id("generic", "Generic deprecation", GradleCoreProblemGroup.deprecation())
            .contextualLabel(label);
        spec.execute(deprecationBuilder);
        return reportBuiltProblem(deprecationBuilder);
    }

    @Override
    public Problem deprecateMethod(Class<?> containingClass, String signature, Action<DeprecateMethodSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reporter.createProblemBuilder());
        deprecationBuilder.getProblemBuilder()
            .id("method", "Method deprecation", GradleCoreProblemGroup.deprecation())
            .contextualLabel(
                String.format("Method '%s#%s' is deprecated", containingClass.getName(), signature)
            );
        spec.execute(deprecationBuilder);
        return reportBuiltProblem(deprecationBuilder);
    }

    @Override
    public Problem deprecatePlugin(String pluginId, Action<DeprecatePluginSpec> spec) {
        DefaultDeprecationBuilder deprecationBuilder = new DefaultDeprecationBuilder(reporter.createProblemBuilder());
        deprecationBuilder.getProblemBuilder()
            .id("plugin", "Plugin deprecation", GradleCoreProblemGroup.deprecation())
            .contextualLabel(
                String.format("Plugin '%s' is deprecated", pluginId)
            );
        spec.execute(deprecationBuilder);
        return reportBuiltProblem(deprecationBuilder);
    }

    private Problem reportBuiltProblem(DefaultDeprecationBuilder builder) {
        Problem deprecationProblem = builder.build();
        reporter.report(deprecationProblem);
        return deprecationProblem;
    }

}
