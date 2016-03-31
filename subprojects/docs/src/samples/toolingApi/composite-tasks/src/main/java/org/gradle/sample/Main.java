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
package org.gradle.sample;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.connection.GradleConnection;
import org.gradle.tooling.connection.GradleConnectionBuilder;
import org.gradle.tooling.connection.ModelResult;
import org.gradle.tooling.connection.ModelResults;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.Launchable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String... args) {
        GradleConnectionBuilder builder = GradleConnector.newGradleConnection();

        File gradleHome = new File(args[0]);
        File gradleUserHome = new File(args[1]);
        builder.useGradleUserHomeDir(gradleUserHome);

        for (int i = 2; i < args.length; i++) {
            File projectDir = new File(args[i]);
            builder.addParticipant(projectDir).useInstallation(gradleHome);
        }

        File interestingProjectDir = new File(args[args.length - 1]); // last project is the interesting one

        GradleConnection connection = builder.build();
        try {
            ModelResults<GradleProject> gradleProjects = connection.getModels(GradleProject.class);
            BuildIdentifier buildIdentifier = findInterestingBuild(interestingProjectDir, gradleProjects);
            List<Launchable> tasksToRun = findTasksToRun(buildIdentifier, gradleProjects);

            if (tasksToRun.isEmpty()) {
                System.err.println(String.format("Could not find any build tasks to run in ", buildIdentifier));
                System.exit(-1);
            }

            // Run all "build" tasks from the interesting project
            BuildLauncher buildLauncher = connection.newBuild();
            buildLauncher.forLaunchables(tasksToRun);
            buildLauncher.setStandardError(System.err);
            buildLauncher.setStandardOutput(System.out);
            buildLauncher.run();
        } finally {
            connection.close();
        }
    }

    private static List<Launchable> findTasksToRun(BuildIdentifier buildIdentifier, ModelResults<GradleProject> gradleProjects) {
        List<Launchable> tasksToRun = new ArrayList<Launchable>();
        if (buildIdentifier != null) {
            for (ModelResult<GradleProject> gradleProjectModelResult : gradleProjects) {
                GradleProject gradleProject = gradleProjectModelResult.getModel();
                if (interestedInProject(gradleProject, buildIdentifier)) {
                    tasksToRun.addAll(getBuildTasksFromGradleProject(gradleProject));
                }
            }
        }
        return tasksToRun;
    }

    private static BuildIdentifier findInterestingBuild(File interestingProjectDir, ModelResults<GradleProject> gradleProjects) {
        BuildIdentifier buildIdentifier = null;
        for (ModelResult<GradleProject> gradleProjectModelResult : gradleProjects) {
            GradleProject gradleProject = gradleProjectModelResult.getModel();
            if (gradleProject.getProjectDirectory().equals(interestingProjectDir)) {
                buildIdentifier = gradleProject.getProjectIdentifier().getBuildIdentifier();
            }
        }
        return buildIdentifier;
    }

    private static boolean interestedInProject(GradleProject gradleProject, BuildIdentifier buildIdentifier) {
        // Gradle project is a member of the build we're interested in
        return gradleProject.getProjectIdentifier().getBuildIdentifier().equals(buildIdentifier);
    }

    private static List<Launchable> getBuildTasksFromGradleProject(GradleProject gradleProject) {
        List<Launchable> launchables = new ArrayList<Launchable>();
        for (GradleTask task : gradleProject.getTasks()) {
            if (task.getName().equals("build")) {
                launchables.add(task);
            }
        }
        return launchables;
    }
}
