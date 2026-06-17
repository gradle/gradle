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

package org.gradle.initialization;

import kotlin.Unit;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter;

/**
 * Arms a listener that reports a problem when the build's start parameter is mutated after its settings
 * have been evaluated. This layer is added to the settings processing chain only when Isolated Projects
 * is enabled, since that is the only mode in which such mutations are reported.
 */
public class StartParameterMutationReportingSettingsProcessor implements SettingsProcessor {

    private final SettingsProcessor delegate;
    private final IsolatedProjectsProblemsReporter problems;

    public StartParameterMutationReportingSettingsProcessor(SettingsProcessor delegate, IsolatedProjectsProblemsReporter problems) {
        this.delegate = delegate;
        this.problems = problems;
    }

    @Override
    public SettingsState process(GradleInternal gradle, SettingsLocation settingsLocation, ClassLoaderScope buildRootClassLoaderScope, StartParameterInternal startParameter) {
        SettingsState state = delegate.process(gradle, settingsLocation, buildRootClassLoaderScope, startParameter);
        startParameter.setMutationListener(methodSignature ->
            problems.report(factory ->
                factory.problem(null, messageBuilder -> {
                    messageBuilder
                        .text("The start parameter cannot be mutated after settings have been evaluated when Isolated Projects is enabled. ")
                        .text("This happened when calling ")
                        .reference(methodSignature)
                        .text(".");
                    return Unit.INSTANCE;
                }).exception().build()
            )
        );
        return state;
    }
}
