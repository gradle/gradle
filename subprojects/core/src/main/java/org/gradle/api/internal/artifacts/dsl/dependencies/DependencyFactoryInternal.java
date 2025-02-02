/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.dsl.dependencies;

import groovy.lang.Closure;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Map;

/**
 * Internal API for dependency creation.
 */
@ServiceScope({Scope.Build.class, Scope.Project.class})
public interface DependencyFactoryInternal extends DependencyFactory {
    //for gradle distribution specific dependencies
    enum ClassPathNotation {
        GRADLE_API("Gradle API"),
        GRADLE_KOTLIN_DSL("Gradle Kotlin DSL"),
        GRADLE_TEST_KIT("Gradle TestKit"),
        LOCAL_GROOVY("Local Groovy"),
        LOCAL_PROJECT_AS_OPAQUE_DEPENDENCY("Local project as an opaque dependency");

        public final String displayName;

        ClassPathNotation(String displayName) {
            assert displayName != null : "display name cannot be null";
            this.displayName = displayName;
        }
    }

    Dependency createDependency(Object dependencyNotation);
    @Deprecated
    org.gradle.api.artifacts.ClientModule createModule(Object dependencyNotation, Closure configureClosure);
    ProjectDependency createProjectDependencyFromMap(ProjectFinder projectFinder, Map<? extends String, ? extends Object> map);
}
