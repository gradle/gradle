/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.api.JavaVersion
import org.gradle.util.internal.VersionNumber

/**
 * Implementing this trait means that a class knows how to create runners for testing Kotlin plugins.
 */
trait KotlinRunnerFactory {
    private static final String PARALLEL_TASKS_IN_PROJECT_PROPERTY = 'kotlin.parallel.tasks.in.project'

    SmokeTestGradleRunner createRunner(ParallelTasksInProject parallelTasksInProject, VersionNumber kotlinVersionNumber, VersionNumber agpVersionNumber, String... tasks) {
        return runnerFor(this, parallelTasksInProject, kotlinVersionNumber, tasks)
                .deprecations(KotlinPluginSmokeTest.KotlinDeprecations) {
                    expectOrgGradleUtilWrapUtilDeprecation(kotlinVersionNumber) {
                        cause = "plugin 'kotlin-android'"
                    }
                    expectAndroidBasePluginConventionDeprecation(kotlinVersionNumber, agpVersionNumber) {
                        causes = [
                            null,
                            "plugin 'com.android.internal.application'"
                        ]
                    }
                    expectAndroidProjectConventionDeprecation(kotlinVersionNumber, agpVersionNumber) {
                        causes = [
                            null,
                            "plugin 'com.android.internal.application'"
                        ]
                    }
                    expectAndroidConventionTypeDeprecation(kotlinVersionNumber, agpVersionNumber) {
                        causes = [
                            null,
                            "plugin 'kotlin-android'",
                            "plugin 'com.android.internal.application'",
                        ]
                    }
                }
    }

    SmokeTestGradleRunner runner(ParallelTasksInProject parallelTasksInProject, VersionNumber kotlinVersion, String... tasks) {
        return runnerFor(this, parallelTasksInProject, kotlinVersion, tasks)
    }

    SmokeTestGradleRunner runnerFor(AbstractSmokeTest smokeTest, ParallelTasksInProject parallelTasksInProject, String... tasks) {
        def args = ['--parallel']
        switch (parallelTasksInProject) {
            case ParallelTasksInProject.TRUE: {
                args += ["-P$PARALLEL_TASKS_IN_PROJECT_PROPERTY=true"]
                break
            }
            case ParallelTasksInProject.FALSE: {
                args += ["-P$PARALLEL_TASKS_IN_PROJECT_PROPERTY=false"]
                break
            }
        }

        smokeTest.runner(tasks + (args as Collection<String>) as String[])
                .forwardOutput()
    }

    SmokeTestGradleRunner runnerFor(AbstractSmokeTest smokeTest, ParallelTasksInProject parallelTasksInProject, VersionNumber kotlinVersion, String... tasks) {
        if (kotlinVersion.getMinor() < 5 && JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
            String kotlinOpts = "-Dkotlin.daemon.jvm.options=--add-exports=java.base/sun.nio.ch=ALL-UNNAMED,--add-opens=java.base/java.util=ALL-UNNAMED"
            return runnerFor(smokeTest, parallelTasksInProject, tasks + [kotlinOpts] as String[])
        }
        runnerFor(smokeTest, parallelTasksInProject, tasks)
    }

    /**
     * Controls if and how to set the {@code #PARALLEL_TASKS_IN_PROJECT_PROPERTY} property.
     */
    static enum ParallelTasksInProject {
        TRUE,
        FALSE,
        OMIT;

        boolean isPropertyPresent() {
            return this != OMIT
        }
    }
}
