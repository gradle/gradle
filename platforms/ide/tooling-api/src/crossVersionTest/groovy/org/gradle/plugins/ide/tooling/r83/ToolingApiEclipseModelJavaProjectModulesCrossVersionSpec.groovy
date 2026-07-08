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

package org.gradle.plugins.ide.tooling.r83

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.tooling.model.eclipse.EclipseProjectDependency

// Marking a project dependency on a modular project with the "module" classpath attribute was added in Gradle 8.3
// (commit "support modular project dependency"): unlike an external jar, an unbuilt project artifact carries no
// module-info, so EclipseDependenciesCreator falls back to inspecting the target project's module-info source via
// EclipseClassPathUtil.isInferModulePath. That fallback did not exist before 8.3.
@Requires(JdkVersionTestPreconditions.Jdk9OrLater)
@TargetGradleVersion(">=8.3")
class ToolingApiEclipseModelJavaProjectModulesCrossVersionSpec extends ToolingApiSpecification {

    def "project dependency on a modular project is marked as a module"() {
        setup:
        includeProjects("api", "util")
        file("api/src/main/java/module-info.java") << """
            module api {
                exports api;
            }
        """
        file("util/src/main/java/module-info.java") << """
            module util {
                requires api;
            }
        """
        file("api/build.gradle") << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }
        """
        file("util/build.gradle") << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }

            dependencies {
                implementation project(':api')
            }
        """

        when:
        EclipseProject rootProject = loadToolingModel(EclipseProject)
        EclipseProject utilProject = rootProject.children.find { it.gradleProject.path == ':util' }
        EclipseProjectDependency apiDependency = utilProject.projectDependencies.find { it.path == 'api' }

        then:
        isModule(apiDependency)
    }

    private static boolean isModule(EclipseClasspathEntry entry) {
        entry.classpathAttributes.find { it.name == 'module' && it.value == 'true' }
    }
}
