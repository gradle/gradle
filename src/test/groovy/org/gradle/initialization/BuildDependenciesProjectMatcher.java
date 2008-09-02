/*
 * Copyright 2007-2008 the original author or authors.
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

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Factory;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.Settings;
import org.gradle.api.DependencyManager;

/**
 * @author Hans Dockter
 */
public class BuildDependenciesProjectMatcher extends BaseMatcher {
    private StartParameter startParameter;

    public BuildDependenciesProjectMatcher(StartParameter startParameter) {
        this.startParameter = startParameter;
    }

    public void describeTo(Description description) {
        description.appendValue(startParameter);
    }

    public boolean matches(Object o) {
        Project project = (Project) o;
        boolean result1, result2, result3, result4;
        result1 = startParameter.getGradleUserHomeDir().getAbsolutePath().equals(project.getGradleUserHome());
        result2 = project.getName().equals(Settings.BUILD_DEPENDENCIES_PROJECT_NAME);
        result3 = project.property(DependencyManager.GROUP).equals(Settings.BUILD_DEPENDENCIES_PROJECT_GROUP);
        result4 = project.property(DependencyManager.VERSION).equals(Settings.BUILD_DEPENDENCIES_PROJECT_VERSION);
        return (result1 && result2 && result3 && result4);
    }

    @Factory
    public static Matcher<Project> equalsBuildProject(StartParameter startParameter) {
        return new BuildDependenciesProjectMatcher(startParameter);
    }
}
