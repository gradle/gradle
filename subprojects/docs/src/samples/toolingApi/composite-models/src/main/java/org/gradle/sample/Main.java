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

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.connection.*;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.gradle.tooling.model.eclipse.EclipseProject;

import java.io.File;
import java.util.Set;

/**
 * Example application that can query the EclipseProject model of a composite of independent Gradle builds.
 */
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
        GradleConnection connection = builder.build();
        try {
            Iterable<ModelResult<EclipseProject>> modelResults = connection.getModels(EclipseProject.class);
            for (ModelResult<EclipseProject> modelResult : modelResults) {
                EclipseProject eclipseProject = modelResult.getModel();
                EclipseProject rootProject = findRootProject(eclipseProject);
                String rootProjectName = rootProject.getGradleProject().getName();

                GradleProject gradleProject = eclipseProject.getGradleProject();
                String projectPath = gradleProject.getPath();
                String projectDir = getProjectDirectory(gradleProject);
                String sourceLanguageLevel = getJavaVersion(eclipseProject);

                System.out.println("Project: " + rootProjectName + ":" + projectPath);
                System.out.println("Source Language Level: " + sourceLanguageLevel);
                System.out.println("Project Directory: " + projectDir);

            }
        } finally {
            connection.close();
        }
    }

    private static String getJavaVersion(EclipseProject eclipseProject) {
        // Requires build with >Gradle 2.10
        try {
            return String.valueOf(eclipseProject.getJavaSourceSettings().getSourceLanguageLevel());
        } catch (Exception e) {
            return "(unknown)";
        }
    }

    private static String getProjectDirectory(GradleProject gradleProject) {
        // Requires build with >Gradle 2.4
        try {
            return gradleProject.getProjectDirectory().getAbsolutePath();
        } catch (UnsupportedMethodException e) {
            return "(unknown project dir)";
        }
    }

    private static EclipseProject findRootProject(EclipseProject eclipseProject) {
        EclipseProject parent = eclipseProject.getParent();
        if (parent!=null) {
            return findRootProject(parent);
        }
        return eclipseProject;
    }
}
