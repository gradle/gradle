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

package org.gradle.performance.plugin;

import org.gradle.BuildResult;
import org.gradle.api.Project;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildRequestMetaData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

import static org.gradle.performance.plugin.ReflectionUtil.*;


public class BuildEventTimeStamps {
    private static long settingsEvaluatedTimestampIfNotAvailable;
    private static long configurationEndTimestamp;
    private static boolean useBuildTimeClock = true;

    public static void settingsEvaluated() {
        settingsEvaluatedTimestampIfNotAvailable = System.nanoTime();
    }

    public static void configurationEvaluated() {
        configurationEndTimestamp = System.nanoTime();
    }

    public static void buildFinished(BuildResult buildResult) {
        long buildEndTimestamp = System.nanoTime();
        long buildTime = resolveBuildTime(buildResult);

        Project project = buildResult.getGradle().getRootProject();
        final long settingsEvaluatedTimeStamp = resolveSettingsEvaluatedTimeStamp(project);

        File buildDir = project.getBuildDir();
        buildDir.mkdirs();
        File timestampsFile = new File(buildDir, "buildEventTimestamps.txt");

        try {
            writeTimeStamps(timestampsFile, settingsEvaluatedTimeStamp, buildEndTimestamp, buildTime);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void writeTimeStamps(File timestampsFile, long settingsEvaluatedTimeStamp, long buildEndTimestamp, long buildTime) throws IOException {
        PrintWriter writer = new PrintWriter(new FileWriter(timestampsFile));
        try {
            writer.println(settingsEvaluatedTimeStamp);
            writer.println(configurationEndTimestamp);
            writer.println(buildEndTimestamp);
            writer.print(buildTime);
        } finally {
            writer.close();
        }
    }

    private static long resolveSettingsEvaluatedTimeStamp(Project project) {
        return project.hasProperty("settingsEvaluatedTimestamp") ? (Long) project.property("settingsEvaluatedTimestamp") : settingsEvaluatedTimestampIfNotAvailable;
    }

    private static long resolveBuildTime(BuildResult buildResult) {
        if (!useBuildTimeClock) {
            return 0;
        }
        try {
            return getService(buildResult.getGradle(), BuildRequestMetaData.class).getBuildTimeClock().getTimeInMs();
        } catch (Exception e) {
            System.err.println("Exception in getting build time " + e.getMessage());
            useBuildTimeClock = false;
            return 0;
        }
    }

    // Use reflection to get services because internal API is not the same in older versions
    private static <T> T getService(Gradle gradle, Class<T> serviceType) {
        Object serviceFactory = invokeMethod(gradle, findMethodByName(gradle.getClass(), "getServices"));
        return (T) invokeMethod(serviceFactory, findMethod(serviceFactory.getClass(), "get", Class.class), serviceType);
    }
}
