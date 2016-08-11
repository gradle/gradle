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

package org.gradle.performance.plugin

import groovy.transform.CompileStatic
import org.gradle.BuildResult
import org.gradle.api.internal.GradleInternal


@CompileStatic
class BuildEventTimeStamps {
    static long settingsEvaluatedTimestampIfNotAvailable
    static long configurationEndTimestamp

    static void settingsEvaluated() {
        settingsEvaluatedTimestampIfNotAvailable = System.nanoTime()
    }

    static void configurationEvaluated() {
        configurationEndTimestamp = System.nanoTime()
    }

    static void buildFinished(BuildResult buildResult) {
        def buildEndTimestamp = System.nanoTime()
        def project = buildResult.gradle.rootProject
        def buildDir = project.buildDir
        buildDir.mkdirs()
        def writer = new File(buildDir, "buildEventTimestamps.txt").newPrintWriter()
        try {
            writer.println(project.hasProperty('settingsEvaluatedTimestamp') ? project.property('settingsEvaluatedTimestamp') : settingsEvaluatedTimestampIfNotAvailable)
            writer.println(configurationEndTimestamp)
            writer.println(buildEndTimestamp)
            try {
                GradleInternal gradleInternal = (GradleInternal) buildResult.gradle
                def buildTime = gradleInternal.services.get(org.gradle.initialization.BuildRequestMetaData).buildTimeClock.timeInMs
                writer.print(buildTime)
            } catch (Exception e) {
            }
        } finally {
            writer.close()
        }
    }
}
