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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.api.tasks.testing.Test;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.language.scala.tasks.PlatformScalaCompile;
import org.gradle.model.Finalize;
import org.gradle.model.Mutate;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.model.collection.CollectionBuilder;
import org.gradle.platform.base.BinaryContainer;
import org.gradle.play.PlayApplicationBinarySpec;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;
import org.gradle.play.platform.PlayPlatform;

import java.io.File;
import java.util.Arrays;
import java.util.Set;

/**
 * Plugin for executing tests from a Play Framework application.
 */
@SuppressWarnings("UnusedDeclaration")
@RuleSource
@Incubating
public class PlayTestPlugin {
    @Mutate
    void createTestTasks(CollectionBuilder<Task> tasks, BinaryContainer binaryContainer, PlayToolChainInternal playToolChain, final FileResolver fileResolver, final ProjectIdentifier projectIdentifier, @Path("buildDir") final File buildDir) {
        for (final PlayApplicationBinarySpec binary : binaryContainer.withType(PlayApplicationBinarySpec.class)) {
            final PlayPlatform targetPlatform = binary.getTargetPlatform();
            FileCollection playTestDependencies = playToolChain.select(targetPlatform).getPlayTestDependencies();
            final FileCollection testCompileClasspath = fileResolver.resolveFiles(binary.getJarFile()).plus(playTestDependencies);

            final String testCompileTaskName = String.format("compile%sTests", StringUtils.capitalize(binary.getName()));
            // TODO:DAZ Model a test suite
            final File testSourceDir = fileResolver.resolve("test");
            final File testClassesDir = new File(buildDir, String.format("%s/testClasses", binary.getName()));
            tasks.create(testCompileTaskName, PlatformScalaCompile.class, new Action<PlatformScalaCompile>() {
                public void execute(PlatformScalaCompile scalaCompile) {
                    scalaCompile.dependsOn(binary.getBuildTask());
                    scalaCompile.setClasspath(testCompileClasspath);
                    scalaCompile.setPlatform(binary.getTargetPlatform().getScalaPlatform());
                    scalaCompile.setDestinationDir(testClassesDir);
                    scalaCompile.setSource(testSourceDir);
                    String targetCompatibility = binary.getTargetPlatform().getJavaPlatform().getTargetCompatibility().getMajorVersion();
                    scalaCompile.setSourceCompatibility(targetCompatibility);
                    scalaCompile.setTargetCompatibility(targetCompatibility);

                    IncrementalCompileOptions incrementalOptions = scalaCompile.getScalaCompileOptions().getIncrementalOptions();
                    incrementalOptions.setAnalysisFile(new File(buildDir, String.format("tmp/scala/compilerAnalysis/%s.analysis", testCompileTaskName)));

                    binary.getTasks().add(scalaCompile);
                }
            });

            final String testTaskName = String.format("test%s", StringUtils.capitalize(binary.getName()));
            final File binaryBuildDir = new File(buildDir, binary.getName());
            tasks.create(testTaskName, Test.class, new Action<Test>() {
                public void execute(Test test) {
                    test.setTestClassesDir(testClassesDir);
                    final FileCollection testRuntimeClasspath = testCompileClasspath.plus(fileResolver.resolveFiles(testClassesDir));
                    test.setClasspath(testRuntimeClasspath);
                    test.setBinResultsDir(new File(binaryBuildDir, String.format("results/%s/bin", testTaskName)));
                    test.getReports().getJunitXml().setDestination(new File(binaryBuildDir, "reports/test/xml"));
                    test.getReports().getHtml().setDestination(new File(binaryBuildDir, "reports/test"));
                    test.dependsOn(testCompileTaskName);
                    test.setTestSrcDirs(Arrays.asList(testSourceDir));
                    test.setWorkingDir(projectIdentifier.getProjectDir());

                    binary.getTasks().add(test);
                    // TODO:DAZ Would be good if we could add as a dependency to 'check' lifecycle task here.
                }
            });
        }
    }

    // TODO Need a better mechanism to wire tasks into lifecycle
    @Finalize
    public void wireTestTasksIntoCheckLifecycle(final TaskContainer tasks, BinaryContainer binaryContainer) {
        Set<PlayApplicationBinarySpec> playBinaries = binaryContainer.withType(PlayApplicationBinarySpec.class);
        for (final PlayApplicationBinarySpec binary : playBinaries) {
            tasks.getByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(binary.getTasks().withType(Test.class));
        }
    }
}