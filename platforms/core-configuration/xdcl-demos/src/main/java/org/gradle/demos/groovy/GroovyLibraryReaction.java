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

package org.gradle.demos.groovy;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.demos.common.DependencyScopes;
import org.gradle.demos.common.Repositories;
import org.gradle.demos.groovy.dsl.GroovyLibrary;
import org.gradle.demos.groovy.dsl.GroovySource;
import org.gradle.jvm.tasks.Jar;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Reacts to a {@code groovyLibrary { }} definition by configuring the live {@link Project} — registering
 * project repositories, the four-scope dependency configurations and resolvable classpaths, the Groovy
 * compiler/runtime classpath, the per-source compile/resource tasks, a jar, and a test task — and
 * publishing the build outputs through a {@link GroovyLibraryModel} Project extension.
 */
public class GroovyLibraryReaction implements Reaction<GroovyLibrary, Project> {

    /** The conventional name of the test source set. */
    private static final String TEST_SOURCE = "test";

    @Override
    public void on(GroovyLibrary data, Project project, ReactionScope scope) {
        if (project.getExtensions().findByType(GroovyLibraryModel.class) != null) {
            return; // already configured by a prior activation — reactions may run more than once
        }
        GroovyLibraryModel model = project.getExtensions().create("groovyLibraryModel", GroovyLibraryModel.class);

        Repositories.configure(data, project);
        DependencyScopes.createShared(data, project);

        // The mandatory Groovy compiler/runtime classpath. GroovyCompile.checkGroovyClasspathIsNonEmpty()
        // throws if this is empty and there is no GroovyBasePlugin here to supply it, so the reaction
        // resolves `org.apache.groovy:groovy:<groovyVersion>` itself. Created once and reused by every
        // GroovyCompile, and added to the test classpath so compiled Groovy runs.
        FileCollection groovyClasspath = createGroovyClasspath(data, project);

        int javaVersion = data.javaVersion().get();
        List<GroovySource> sources = data.sources().getOrElse(List.of());

        // The compiled bytecode of every non-test source set — the "production" classpath the test
        // source set compiles and runs against. Lazy: the byteCodeDir providers carry their
        // compile-task dependencies.
        FileCollection productionClasses = project.files((Callable<Object>) () ->
            model.getClasses().stream()
                .filter(classes -> !TEST_SOURCE.equals(classes.getName()))
                .map(GroovyClasses::getByteCodeDir)
                .toList());

        for (GroovySource source : sources) {
            String name = source.name().get();
            String cap = capitalize(name);

            FileCollection sourceClasspath = DependencyScopes.configureSource(project, name, source);
            FileCollection compileClasspath = TEST_SOURCE.equals(name)
                ? sourceClasspath.plus(productionClasses).plus(groovyClasspath)
                : sourceClasspath.plus(groovyClasspath);

            Object groovySrc = orConvention(source.groovyDirs().getOrElse(List.of()), "src/" + name + "/groovy");
            Object resourceSrc = orConvention(source.resourceDirs().getOrElse(List.of()), "src/" + name + "/resources");

            TaskProvider<GroovyCompile> compile = project.getTasks().register("compile" + cap + "Groovy", GroovyCompile.class, task -> {
                task.setGroup("build");
                task.setDescription("Compiles the " + name + " Groovy source.");
                task.setSource(groovySrc);
                // A standalone GroovyCompile (no GroovyBasePlugin) has no convention for source/target
                // compatibility, so set them explicitly from the declared javaVersion.
                task.setSourceCompatibility(String.valueOf(javaVersion));
                task.setTargetCompatibility(String.valueOf(javaVersion));
                task.setClasspath(compileClasspath);
                // Mandatory: the Groovy compiler/runtime jars.
                task.setGroovyClasspath(groovyClasspath);
                task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("classes/groovy/" + name));
            });

            TaskProvider<Copy> processResources = project.getTasks().register("process" + cap + "Resources", Copy.class, task -> {
                task.setGroup("build");
                task.setDescription("Processes the " + name + " resources.");
                task.from(resourceSrc);
                task.into(project.getLayout().getBuildDirectory().dir("resources/" + name));
            });

            model.getClasses().create(name, classes -> {
                classes.getInputSources().from(groovySrc);
                classes.getByteCodeDir().set(compile.flatMap(GroovyCompile::getDestinationDirectory));
                classes.getProcessedResourcesDir().fileProvider(processResources.map(Copy::getDestinationDir));
            });
        }

        GroovyClasses mainClasses = model.getClasses().findByName("main");
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

        registerTestTask(project, model, mainClasses, productionClasses, groovyClasspath);

        List<String> sourceNames = sources.stream().map(s -> s.name().get()).toList();
        project.getLogger().lifecycle("groovyLibrary[" + project.getName() + "] groovyVersion=" + data.groovyVersion().getOrElse("") + " javaVersion=" + javaVersion + " sources=" + sourceNames);
    }

    /**
     * Build the resolvable {@code groovyClasspath}: a configuration holding
     * {@code org.apache.groovy:groovy:<groovyVersion>}, with the standard runtime-library attributes so
     * it selects the groovy runtime variant. {@code groovyVersion} is normally supplied by the
     * {@code groovy-ecosystem} shipped default; a missing one is a configuration error.
     */
    private static FileCollection createGroovyClasspath(GroovyLibrary data, Project project) {
        String version = data.groovyVersion().getOrNull();
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("groovyLibrary requires a groovyVersion (normally supplied by the groovy-ecosystem default)");
        }
        ConfigurationContainer configurations = project.getConfigurations();
        configurations.dependencyScope("groovyRuntime");
        project.getDependencies().add("groovyRuntime", "org.apache.groovy:groovy:" + version);
        return configurations.resolvable("groovyClasspath", c -> {
            c.extendsFrom(configurations.getByName("groovyRuntime"));
            c.attributes(a -> {
                a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                a.attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY));
                a.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.getObjects().named(LibraryElements.class, LibraryElements.JAR));
            });
        }).get();
    }

    /**
     * Register the {@code test} task (JUnit 4) and wire its report locations into the model. Its
     * classpath is the test source set's resolvable runtime classpath (which carries any test
     * framework declared on the {@code test} source's {@code dependencies}), the test and production
     * bytecode, plus the {@code groovyClasspath} so the compiled Groovy classes can be loaded and run.
     */
    private static void registerTestTask(Project project, GroovyLibraryModel model, GroovyClasses mainClasses, FileCollection productionClasses, FileCollection groovyClasspath) {
        GroovyClasses testClasses = model.getClasses().findByName(TEST_SOURCE);
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
                .plus(productionClasses)
                .plus(groovyClasspath));
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
