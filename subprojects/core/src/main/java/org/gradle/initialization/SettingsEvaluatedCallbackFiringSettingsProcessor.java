/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter;

public class SettingsEvaluatedCallbackFiringSettingsProcessor implements SettingsProcessor {

    private final SettingsProcessor delegate;
    private final IsolatedProjectsProblemsReporter problems;

    public SettingsEvaluatedCallbackFiringSettingsProcessor(SettingsProcessor delegate, IsolatedProjectsProblemsReporter problems) {
        this.delegate = delegate;
        this.problems = problems;
    }

    @Override
    public SettingsState process(GradleInternal gradle, SettingsLocation settingsLocation, ClassLoaderScope buildRootClassLoaderScope, StartParameterInternal startParameter) {
        SettingsState state = delegate.process(gradle, settingsLocation, buildRootClassLoaderScope, startParameter);
        SettingsInternal settings = state.getSettings();
        gradle.getBuildListenerBroadcaster().settingsEvaluated(settings);
        settings.preventFromFurtherMutation();
        startParameter.setMutationListener(methodSignature ->
            problems.report(factory ->
                factory.problem(null, messageBuilder -> {
                    messageBuilder.text(
                        "Cannot call '" + methodSignature + "' on StartParameter after settings have been evaluated when Isolated Projects is enabled."
                    );
                    return Unit.INSTANCE;
                }).exception().build()
            )
        );
        return state;
    }
}
