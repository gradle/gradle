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

package org.gradle.demos.java;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.demos.common.DependencyScopes;
import org.gradle.demos.common.Repositories;
import org.gradle.demos.java.dsl.JavaLibrary;
import org.gradle.demos.java.dsl.JavaSource;
import org.gradle.jvm.tasks.Jar;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * The XDCL port of {@code JavaProjectTypePlugin.ApplyAction}: reacts to a {@code javaLibrary { }}
 * definition by configuring the live {@link Project} — registering project repositories, the
 * four-scope dependency configurations and resolvable classpaths, the per-source compile/resource
 * tasks, a jar, and a test task — and publishing the build outputs through a {@link JavaLibraryModel}
 * Project extension (XDCL's stand-in for the project-features build model).
 *
 * <p>Stateless per the {@link Reaction} contract; idempotent via the extension's presence.
 */
public class JavaLibraryReaction implements Reaction<JavaLibrary, Project> {

    /** The conventional name of the test source set. */
    private static final String TEST_SOURCE = "test";

    @Override
    public void on(JavaLibrary data, Project project, ReactionScope scope) {
        if (project.getExtensions().findByType(JavaLibraryModel.class) != null) {
            return; // already configured by a prior activation — reactions may run more than once
        }
        JavaLibraryModel model = project.getExtensions().create("javaLibraryModel", JavaLibraryModel.class);

        Repositories.configure(data, project);
        DependencyScopes.createShared(data, project);

        int javaVersion = data.javaVersion().get();
        List<JavaSource> sources = data.sources().getOrElse(List.of());

        // The compiled bytecode of every non-test source set — the "production" classpath the test
        // source set compiles and runs against, so a test can exercise the other sources. Lazy: the
        // byteCodeDir providers carry their compile-task dependencies.
        FileCollection productionClasses = project.files((Callable<Object>) () ->
            model.getClasses().stream()
                .filter(classes -> !TEST_SOURCE.equals(classes.getName()))
                .map(JavaClasses::getByteCodeDir)
                .toList());

        for (JavaSource source : sources) {
            String name = source.name().get();
            String cap = capitalize(name);

            FileCollection sourceClasspath = DependencyScopes.configureSource(project, name, source);
            FileCollection compileClasspath = TEST_SOURCE.equals(name) ? sourceClasspath.plus(productionClasses) : sourceClasspath;

            Object javaSrc = orConvention(source.javaDirs().getOrElse(List.of()), "src/" + name + "/java");
            Object resourceSrc = orConvention(source.resourceDirs().getOrElse(List.of()), "src/" + name + "/resources");

            TaskProvider<JavaCompile> compile = project.getTasks().register("compile" + cap + "Java", JavaCompile.class, task -> {
                task.setGroup("build");
                task.setDescription("Compiles the " + name + " Java source.");
                task.setSource(javaSrc);
                // A standalone JavaCompile (no JavaBasePlugin) has no convention for source/target
                // compatibility, so set them explicitly from the declared javaVersion.
                task.setSourceCompatibility(String.valueOf(javaVersion));
                task.setTargetCompatibility(String.valueOf(javaVersion));
                task.setClasspath(compileClasspath);
                task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("classes/java/" + name));
            });

            TaskProvider<Copy> processResources = project.getTasks().register("process" + cap + "Resources", Copy.class, task -> {
                task.setGroup("build");
                task.setDescription("Processes the " + name + " resources.");
                task.from(resourceSrc);
                task.into(project.getLayout().getBuildDirectory().dir("resources/" + name));
            });

            model.getClasses().create(name, classes -> {
                classes.getInputSources().from(javaSrc);
                classes.getByteCodeDir().set(compile.flatMap(JavaCompile::getDestinationDirectory));
                classes.getProcessedResourcesDir().fileProvider(processResources.map(Copy::getDestinationDir));
            });
        }

        JavaClasses mainClasses = model.getClasses().findByName("main");
        if (mainClasses != null) {
            TaskProvider<Jar> jar = project.getTasks().register("jar", Jar.class, task -> {
                task.setGroup("build");
                task.setDescription("Assembles a jar archive containing the main classes.");
                task.from(mainClasses.getByteCodeDir());
                task.from(mainClasses.getProcessedResourcesDir());
                // A standalone Jar (no base plugin) has no archive conventions.
                task.getArchiveBaseName().set(project.getName());
                task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("libs"));
            });
            model.getJarFile().set(jar.flatMap(Jar::getArchiveFile));
        }

        registerTestTask(project, model, mainClasses, productionClasses);

        List<String> sourceNames = sources.stream().map(s -> s.name().get()).toList();
        project.getLogger().lifecycle("javaLibrary[" + project.getName() + "] javaVersion=" + javaVersion + " sources=" + sourceNames);
    }

    /**
     * Register the {@code test} task (JUnit 4) and wire its report locations into the model. Its
     * classpath is the test source set's resolvable runtime classpath (which carries any test
     * framework declared on the {@code test} source's {@code dependencies}) plus the test and
     * production bytecode, so {@code :test} compiles and runs against the other source sets.
     */
    private static void registerTestTask(Project project, JavaLibraryModel model, JavaClasses mainClasses, FileCollection productionClasses) {
        JavaClasses testClasses = model.getClasses().findByName(TEST_SOURCE);
        if (testClasses == null || mainClasses == null) {
            return;
        }
        TaskProvider<Test> test = project.getTasks().register("test", Test.class, task -> {
            task.setGroup("verification");
            task.setDescription("Runs the test suite.");
            task.useJUnit();
            task.setTestClassesDirs(project.files(testClasses.getByteCodeDir()));
            task.setClasspath(project.getConfigurations().getByName("testRuntimeClasspath")
                .plus(project.files(testClasses.getByteCodeDir()))
                .plus(productionClasses));
            // A standalone Test (no java plugin) has no convention for its binary results location.
            task.getBinaryResultsDirectory().set(project.getLayout().getBuildDirectory().dir("test-results/test/binary"));
            task.getReports().getHtml().getOutputLocation().set(project.getLayout().getBuildDirectory().dir("reports/tests/test"));
            task.getReports().getJunitXml().getOutputLocation().set(project.getLayout().getBuildDirectory().dir("test-results/test"));
        });
        model.getTestReports().getHtmlReportDir().set(test.flatMap(t -> t.getReports().getHtml().getOutputLocation()));
        model.getTestReports().getJunitXmlReportDir().set(test.flatMap(t -> t.getReports().getJunitXml().getOutputLocation()));
    }

    /** The override directories if supplied, else a single conventional path. */
    private static Object orConvention(List<String> override, String convention) {
        return override.isEmpty() ? convention : override;
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
