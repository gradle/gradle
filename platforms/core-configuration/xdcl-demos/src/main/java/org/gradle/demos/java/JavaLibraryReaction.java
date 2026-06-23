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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.xdcl.Reaction;
import org.gradle.api.xdcl.ReactionScope;
import org.gradle.demos.java.dsl.Dependencies;
import org.gradle.demos.java.dsl.JavaLibrary;
import org.gradle.demos.java.dsl.JavaSource;
import org.gradle.demos.java.dsl.NamedRepository;
import org.gradle.demos.java.dsl.Repository;
import org.gradle.jvm.tasks.Jar;

import java.util.List;
import java.util.Map;
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

    /** The dependency scopes, in the order the demo wires them. */
    private static final List<String> SCOPES = List.of("api", "implementation", "compileOnly", "runtimeOnly");

    /** The conventional name of the test source set. */
    private static final String TEST_SOURCE = "test";

    @Override
    public void on(JavaLibrary data, Project project, ReactionScope scope) {
        if (project.getExtensions().findByType(JavaLibraryModel.class) != null) {
            return; // already configured by a prior activation — reactions may run more than once
        }
        JavaLibraryModel model = project.getExtensions().create("javaLibraryModel", JavaLibraryModel.class);

        configureRepositories(data, project);
        createSharedDependencyScopes(data, project);

        int javaVersion = data.javaVersion().get();
        Map<String, JavaSource> sources = data.sources().getOrElse(Map.of());

        // The compiled bytecode of every non-test source set — the "production" classpath the test
        // source set compiles and runs against, so a test can exercise the other sources. Lazy: the
        // byteCodeDir providers carry their compile-task dependencies.
        FileCollection productionClasses = project.files((Callable<Object>) () ->
            model.getClasses().stream()
                .filter(classes -> !TEST_SOURCE.equals(classes.getName()))
                .map(JavaClasses::getByteCodeDir)
                .toList());

        for (Map.Entry<String, JavaSource> entry : sources.entrySet()) {
            String name = entry.getKey();
            JavaSource source = entry.getValue();
            String cap = capitalize(name);

            FileCollection sourceClasspath = configureSourceDependencies(project, name, source);
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

        project.getLogger().lifecycle("javaLibrary[" + project.getName() + "] javaVersion=" + javaVersion + " sources=" + sources.keySet());
    }

    /**
     * Configure the project's repositories from the declared notations: a {@code :mavenCentral} /
     * {@code :gradlePluginPortal} builtin symbol maps to the matching builtin repository; a String is
     * a maven repository URL.
     */
    private static void configureRepositories(JavaLibrary data, Project project) {
        for (Repository repository : data.repositories().getOrElse(List.of())) {
            if (repository instanceof NamedRepository named) {
                String symbol = named.value().get();
                switch (symbol) {
                    case "mavenCentral" -> project.getRepositories().mavenCentral();
                    case "gradlePluginPortal" -> project.getRepositories().gradlePluginPortal();
                    default -> throw new IllegalArgumentException("unknown builtin repository: " + symbol);
                }
            } else if (repository instanceof Repository.StringValue url) {
                project.getRepositories().maven(repo -> repo.setUrl(url.value().get()));
            }
        }
    }

    /**
     * Create the four shared (top-level) dependency-scope configurations and add the project-wide
     * {@code dependencies} to them. Each source set's own scopes extend these (see
     * {@link #configureSourceDependencies}). Hand-rolls the slice of {@code JavaBasePlugin} the demo needs.
     */
    private static void createSharedDependencyScopes(JavaLibrary data, Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        for (String scope : SCOPES) {
            configurations.dependencyScope(scope);
        }
        data.dependencies().ifPresent(dependencies -> addScopes(project, "", dependencies));
    }

    /**
     * Create a source set's own dependency-scope configurations ({@code <name>Api}, {@code <name>Implementation},
     * …), each extending the matching top-level scope; add the source's {@code dependencies}; and return its
     * resolvable compile classpath (a {@code <name>RuntimeClasspath} is also created for the runtime/test path).
     */
    private static FileCollection configureSourceDependencies(Project project, String name, JavaSource source) {
        ConfigurationContainer configurations = project.getConfigurations();
        for (String scope : SCOPES) {
            configurations.dependencyScope(scopeName(name, scope), c -> c.extendsFrom(configurations.getByName(scope)));
        }
        source.dependencies().ifPresent(dependencies -> addScopes(project, name, dependencies));

        Configuration compileClasspath = configurations.resolvable(name + "CompileClasspath", c -> {
            c.extendsFrom(
                configurations.getByName(scopeName(name, "api")),
                configurations.getByName(scopeName(name, "implementation")),
                configurations.getByName(scopeName(name, "compileOnly")));
            c.attributes(a -> a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API)));
        }).get();
        configurations.resolvable(name + "RuntimeClasspath", c -> {
            c.extendsFrom(
                configurations.getByName(scopeName(name, "api")),
                configurations.getByName(scopeName(name, "implementation")),
                configurations.getByName(scopeName(name, "runtimeOnly")));
            c.attributes(a -> a.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME)));
        });
        return compileClasspath;
    }

    /** Add a {@link Dependencies} block's coordinates to the scope configurations under [prefix] ("" = top level). */
    private static void addScopes(Project project, String prefix, Dependencies dependencies) {
        addAll(project, scopeName(prefix, "api"), dependencies.api());
        addAll(project, scopeName(prefix, "implementation"), dependencies.implementation());
        addAll(project, scopeName(prefix, "runtimeOnly"), dependencies.runtimeOnly());
        addAll(project, scopeName(prefix, "compileOnly"), dependencies.compileOnly());
    }

    /** The configuration name for a dependency scope: bare at the top level, {@code <prefix><Scope>} per source. */
    private static String scopeName(String prefix, String scope) {
        return prefix.isEmpty() ? scope : prefix + capitalize(scope);
    }

    private static void addAll(Project project, String configuration, Provider<List<String>> notations) {
        for (String notation : notations.getOrElse(List.of())) {
            project.getDependencies().add(configuration, notation);
        }
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
