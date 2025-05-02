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

package org.gradle.internal.enterprise.impl;


import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.configuration.BuildFeatures;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.enterprise.DevelocityBuildLifecycleService;

import javax.inject.Inject;

public class DefaultDevelocityBuildLifecycleService implements DevelocityBuildLifecycleService {

    private final Gradle gradle;
    private final BuildFeatures buildFeatures;

    @Inject
    public DefaultDevelocityBuildLifecycleService(Gradle gradle, BuildFeatures buildFeatures) {
        this.gradle = gradle;
        this.buildFeatures = buildFeatures;
    }

    @Override
    public void beforeProject(Action<? super Project> action) {
        // GradleLifecycle#beforeProject isolates the action to be safe with Isolated Projects.
        // This brings additional serializability requirements for any state referenced by the action.
        // Therefore, the new callback cannot be used without Isolated Projects, because it implies a breaking change in behavior
        if (isIsolatedProjects()) {
            gradle.getLifecycle().beforeProject(action::execute);
        } else {
            gradle.allprojects(action);
        }
    }

    private boolean isIsolatedProjects() {
        return buildFeatures.getIsolatedProjects().getActive().getOrElse(false);
    }
}
