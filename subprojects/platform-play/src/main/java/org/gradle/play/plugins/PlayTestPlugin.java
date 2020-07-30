/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins;

import org.apache.commons.lang.WordUtils;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.model.ModelMap;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.internal.PlayApplicationBinarySpecInternal;

import java.io.File;

/**
 * Plugin for executing tests from a Play Framework application.
 */
@SuppressWarnings("UnusedDeclaration")
@Incubating
@Deprecated
public class PlayTestPlugin extends RuleSource {
    @Mutate
    void createTestTasks(ModelMap<Task> tasks, @Path("binaries") ModelMap<PlayApplicationBinarySpecInternal> playBinaries, final PlayPluginConfigurations configurations,
                         final ProjectLayout projectLayout, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
        DeprecationLogger.deprecatePlugin("Play Test")
            .replaceWithExternalPlugin("org.gradle.playframework-test")
            .willBeRemovedInGradle7()
            .withUserManual("play_plugin")
            .nagUser();
        for (final PlayApplicationBinarySpecInternal binary : playBinaries) {
            final FileCollection binaryOutputs = projectLayout.files(binary.getJarFile());
            final FileCollection testCompileClasspath = binaryOutputs.plus(configurations.getPlayTest().getAllArtifacts());

            final String testCompileTaskName = binary.getTasks().taskName("compile", "tests");
            final Directory testSourceDir = projectLayout.getProjectDirectory().dir("test");
            final FileCollection testSources = testSourceDir.getAsFileTree().matching(new PatternSet().include("**/*.scala", "**/*.java"));
            final Provider<Directory> testClassesDir = projectLayout.getBuildDirectory().dir(binary.getProjectScopedName() + "/testClasses");
            tasks.create(testCompileTaskName, PlatformScalaCompile.class, scalaCompile -> {
                scalaCompile.setDescription("Compiles the scala and java test sources for the " + binary.getDisplayName() + ".");

                scalaCompile.setClasspath(testCompileClasspath);

                scalaCompile.dependsOn(binary.getBuildTask());
                scalaCompile.setPlatform(binary.getTargetPlatform().getScalaPlatform());
                scalaCompile.getDestinationDirectory().set(testClassesDir);
                scalaCompile.setSource(testSources);
                String targetCompatibility = binary.getTargetPlatform().getJavaPlatform().getTargetCompatibility().getMajorVersion();
                scalaCompile.setSourceCompatibility(targetCompatibility);
                scalaCompile.setTargetCompatibility(targetCompatibility);

                IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                incrementalOptions.getAnalysisFile().set(new File(buildDir, "tmp/scala/compilerAnalysis/" + testCompileTaskName + ".analysis"));
                incrementalOptions.getClassfileBackupDir().set(new File(buildDir, "tmp/scala/classfileBackup/" + testCompileTaskName + ".bak"));

            });

            final String testTaskName = binary.getTasks().taskName("test");
            final File binaryBuildDir = new File(buildDir, binary.getProjectScopedName());
            final FileCollection testClassesDirs = projectLayout.files(testClassesDir);
            tasks.create(testTaskName, Test.class, test -> {
                test.setDescription("Runs " + WordUtils.uncapitalize(binary.getDisplayName() + "."));

                test.setTestClassesDirs(testClassesDirs);
                test.setBinResultsDir(new File(binaryBuildDir, "results/" + testTaskName + "/bin"));
                test.getReports().getJunitXml().setDestination(new File(binaryBuildDir, "reports/test/xml"));
                test.getReports().getHtml().setDestination(new File(binaryBuildDir, "reports/test"));
                test.dependsOn(testCompileTaskName);
                test.setWorkingDir(projectIdentifier.getProjectDir());
            });
            tasks.withType(Test.class).named(testTaskName, test -> test.setClasspath(testClassesDirs.plus(testCompileClasspath)));
            binary.getTasks().add(tasks.get(testTaskName));
        }
    }

    @Mutate
    void attachTestSuitesToCheckTask(ModelMap<Task> tasks, @Path("binaries") final ModelMap<PlayApplicationBinarySpec> playBinaries) {
        // TODO - binaries aren't an input to this rule, they're an input to the action
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, checkTask -> {
            // TODO Need a better mechanism to wire tasks into lifecycle
            for (PlayApplicationBinarySpec binary : playBinaries) {
                checkTask.dependsOn(binary.getTasks().withType(Test.class));
            }
        });
    }
}
