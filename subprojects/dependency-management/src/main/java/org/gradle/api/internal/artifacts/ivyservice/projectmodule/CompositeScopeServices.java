/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.StartParameter;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRuleProvider;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.service.ServiceRegistry;

public class CompositeScopeServices {
    private final StartParameter startParameter;
    private final ServiceRegistry compositeServices;

    public CompositeScopeServices(StartParameter startParameter, ServiceRegistry compositeServices) {
        this.startParameter = startParameter;
        this.compositeServices = compositeServices;
    }

    ProjectArtifactBuilder createCompositeProjectArtifactBuilder(GradleLauncherFactory  gradleLauncherFactory) {
        return new CompositeProjectArtifactBuilder(gradleLauncherFactory, startParameter, compositeServices);
    }

    DependencySubstitutionRuleProvider createCompositeBuildDependencySubstitutions(CompositeProjectComponentRegistry projectComponentRegistry) {
        return new CompositeBuildDependencySubstitutions(projectComponentRegistry);
    }

}
