/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.performance.fixture


import javax.annotation.Nullable

class AndroidTestProject implements TestProject {

    public static final LARGE_ANDROID_BUILD = new AndroidTestProject(
        templateName: 'largeAndroidBuild'
    )

    public static final List<AndroidTestProject> ANDROID_TEST_PROJECTS = [
        LARGE_ANDROID_BUILD,
        IncrementalAndroidTestProject.SANTA_TRACKER_JAVA,
        IncrementalAndroidTestProject.SANTA_TRACKER_KOTLIN
    ]

    String templateName

    static AndroidTestProject projectFor(String testProject) {
        def foundProject = findProjectFor(testProject)
        if (!foundProject) {
            throw new IllegalArgumentException("Android project ${testProject} not found")
        }
        return foundProject
    }

    @Nullable
    static AndroidTestProject findProjectFor(String testProject) {
        return ANDROID_TEST_PROJECTS.find { it.templateName == testProject }
    }

    @Override
    void configure(CrossVersionPerformanceTestRunner runner) {
    }

    @Override
    void configure(GradleBuildExperimentSpec.GradleBuilder builder) {
    }

    @Override
    String toString() {
        templateName
    }
}

