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

package org.gradle.sonar.runner.plugins;

import com.beust.jcommander.internal.Maps;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvableDependencies;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.jvm.Jvm;
import org.gradle.sonar.runner.SonarRunnerExtension;
import org.gradle.sonar.runner.SonarRunnerRootExtension;
import org.gradle.sonar.runner.tasks.SonarRunner;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.util.CollectionUtils.nonEmptyOrNull;

/**
 * A plugin for analyzing projects with the <a href="http://docs.codehaus.org/display/SONAR/Analyzing+with+Sonar+Runner">Sonar Runner</a>.
 * <p>
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run). Therefore, it's common to apply the plugin only to the root project.
 * To exclude selected subprojects from being analyzed, set {@code sonarRunner.skipProject = true}.
 * <p>
 * The plugin is configured via {@link SonarRunnerExtension}.
 * Here is a small example:
 * <pre autoTested=''>
 * sonarRunner {
 *   skipProject = false // this is the default
 *
 *   sonarProperties {
 *     property "sonar.host.url", "http://my.sonar.server" // adding a single property
 *     properties mapOfProperties // adding multiple properties at once
 *     properties["sonar.sources"] += sourceSets.other.java.srcDirs // manipulating an existing property
 *   }
 * }
 * </pre>
 * <p>
 * The Sonar Runner already comes with defaults for some of the most important Sonar properties (server URL, database settings, etc.).
 * For details see <a href="http://docs.codehaus.org/display/SONAR/Analysis+Parameters">Analysis Parameters</a> in the Sonar documentation.
 * The {@code sonar-runner} plugin provides the following additional defaults:
 * <dl>
 *     <dt>sonar.projectKey
 *     <dd>"$project.group:$project.name"
 *     <dt>sonar.projectName
 *     <dd>project.name
 *     <dt>sonar.projectDescription
 *     <dd>project.description
 *     <dt>sonar.projectVersion
 *     <dd>sonar.version
 *     <dt>sonar.projectBaseDir
 *     <dd>project.projectDir
 *     <dt>sonar.working.directory
 *     <dd>"$project.buildDir/sonar"
 *     <dt>sonar.dynamicAnalysis
 *     <dd>"reuseReports"
 * </dl>
 * <p>
 * For project that have the {@code java-base} plugin applied, additionally the following defaults are provided:
 * <dl>
 *     <dt>sonar.java.source
 *     <dd>project.sourceCompatibility
 *     <dt>sonar.java.target
 *     <dd>project.targetCompatibility
 * </dl>
 * <p>
 * For project that have the {@code java} plugin applied, additionally the following defaults are provided:
 * <dl>
 *     <dt>sonar.sources
 *     <dd>sourceSets.main.allSource.srcDirs (filtered to only include existing directories)
 *     <dt>sonar.tests
 *     <dd>sourceSets.test.allSource.srcDirs (filtered to only include existing directories)
 *     <dt>sonar.binaries
 *     <dd>sourceSets.main.runtimeClasspath (filtered to only include directories)
 *     <dt>sonar.libraries
 *     <dd>sourceSets.main.runtimeClasspath (filtering to only include files; {@code rt.jar} added if necessary)
 *     <dt>sonar.surefire.reportsPath
 *     <dd>test.testResultsDir (if the directory exists)
 *     <dt>sonar.junit.reportsPath
 *     <dd>test.testResultsDir (if the directory exists)
 * </dl>
 */
@Incubating
public class SonarRunnerPlugin implements Plugin<Project> {

    private static final Predicate<File> FILE_EXISTS = new Predicate<File>() {
        public boolean apply(File input) {
            return input.exists();
        }
    };
    private static final Predicate<File> IS_DIRECTORY = new Predicate<File>() {
        public boolean apply(File input) {
            return input.isDirectory();
        }
    };
    private static final Predicate<File> IS_FILE = new Predicate<File>() {
        public boolean apply(File input) {
            return input.isFile();
        }
    };
    private static final Joiner COMMA_JOINER = Joiner.on(",");
    private Project targetProject;

    public void apply(Project project) {
        targetProject = project;
        SonarRunner sonarRunnerTask = createTask(project);

        SonarRunnerRootExtension rootExtension = project.getExtensions().create(SonarRunnerExtension.SONAR_RUNNER_EXTENSION_NAME, SonarRunnerRootExtension.class);
        addConfiguration(project, rootExtension);
        rootExtension.setForkOptions(sonarRunnerTask.getForkOptions());

        project.subprojects(new Action<Project>() {
            public void execute(Project project11) {
                project11.getExtensions().create(SonarRunnerExtension.SONAR_RUNNER_EXTENSION_NAME, SonarRunnerExtension.class);
            }
        });

    }

    private SonarRunner createTask(final Project project) {
        SonarRunner sonarRunnerTask = project.getTasks().create(SonarRunnerExtension.SONAR_RUNNER_TASK_NAME, SonarRunner.class);
        sonarRunnerTask.setDescription("Analyzes " + project + " and its subprojects with Sonar Runner.");

        ConventionMapping conventionMapping = new DslObject(sonarRunnerTask).getConventionMapping();
        conventionMapping.map("sonarProperties", new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Object> properties = Maps.newLinkedHashMap();
                computeSonarProperties(project, properties);
                return properties;
            }
        });

        sonarRunnerTask.dependsOn(new Callable<Iterable<? extends Task>>() {
            public Iterable<? extends Task> call() throws Exception {
                Iterable<Project> applicableProjects = Iterables.filter(project.getAllprojects(), new Predicate<Project>() {
                    public boolean apply(Project input) {
                        return input.getPlugins().hasPlugin(JavaPlugin.class)
                                && !input.getExtensions().getByType(SonarRunnerExtension.class).isSkipProject();
                    }
                });

                return Iterables.transform(applicableProjects, new Function<Project, Task>() {
                    @Nullable
                    public Task apply(Project input) {
                        return input.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
                    }
                });
            }
        });

        return sonarRunnerTask;
    }

    public void computeSonarProperties(Project project, Map<String, Object> properties) {
        SonarRunnerExtension extension = project.getExtensions().getByType(SonarRunnerExtension.class);
        if (extension.isSkipProject()) {
            return;
        }

        Map<String, Object> rawProperties = Maps.newLinkedHashMap();
        addGradleDefaults(project, rawProperties);
        extension.evaluateSonarPropertiesBlocks(rawProperties);
        if (project.equals(targetProject)) {
            addSystemProperties(rawProperties);
        }

        String projectPrefix = project.getPath().substring(targetProject.getPath().length()).replace(":", ".");
        if (projectPrefix.startsWith(".")) {
            projectPrefix = projectPrefix.substring(1);
        }

        convertProperties(rawProperties, projectPrefix, properties);

        List<Project> enabledChildProjects = Lists.newLinkedList(Iterables.filter(project.getChildProjects().values(), new Predicate<Project>() {
            public boolean apply(Project input) {
                return !input.getExtensions().getByType(SonarRunnerExtension.class).isSkipProject();
            }
        }));

        if (enabledChildProjects.isEmpty()) {
            return;
        }

        String modules = COMMA_JOINER.join(Iterables.transform(enabledChildProjects, new Function<Project, String>() {
            public String apply(Project input) {
                return input.getName();
            }
        }));

        properties.put(convertKey("sonar.modules", projectPrefix), modules);
        for (Project childProject : enabledChildProjects) {
            computeSonarProperties(childProject, properties);
        }
    }

    private void addGradleDefaults(final Project project, final Map<String, Object> properties) {
        properties.put("sonar.projectName", project.getName());
        properties.put("sonar.projectDescription", project.getDescription());
        properties.put("sonar.projectVersion", project.getVersion());
        properties.put("sonar.projectBaseDir", project.getProjectDir());
        properties.put("sonar.dynamicAnalysis", "reuseReports");

        if (project.equals(targetProject)) {
            // We only set project key for root project because Sonar Runner 2.0 will automatically
            // prefix subproject keys with parent key, even if subproject keys are set explicitly.
            // Therefore it's better to rely on Sonar's defaults.
            properties.put("sonar.projectKey", getProjectKey(project));
            properties.put("sonar.environment.information.key", "Gradle");
            properties.put("sonar.environment.information.version", project.getGradle().getGradleVersion());
            properties.put("sonar.working.directory", new File(project.getBuildDir(), "sonar"));
        }

        project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
            public void execute(JavaBasePlugin javaBasePlugin) {
                JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);
                properties.put("sonar.java.source", javaPluginConvention.getSourceCompatibility());
                properties.put("sonar.java.target", javaPluginConvention.getTargetCompatibility());
            }
        });

        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            public void execute(JavaPlugin javaPlugin) {
                JavaPluginConvention javaPluginConvention = new DslObject(project).getConvention().getPlugin(JavaPluginConvention.class);

                SourceSet main = javaPluginConvention.getSourceSets().getAt("main");
                properties.put("sonar.sources", nonEmptyOrNull(Iterables.filter(main.getAllSource().getSrcDirs(), FILE_EXISTS)));
                SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
                properties.put("sonar.tests", nonEmptyOrNull(Iterables.filter(test.getAllSource().getSrcDirs(), FILE_EXISTS)));

                properties.put("sonar.binaries", nonEmptyOrNull(Iterables.filter(main.getRuntimeClasspath(), IS_DIRECTORY)));
                properties.put("sonar.libraries", getLibraries(main));

                final Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);
                File testResultsDir = testTask.getReports().getJunitXml().getDestination();
                File testResultsValue = testResultsDir.exists() ? testResultsDir : null;

                properties.put("sonar.surefire.reportsPath", testResultsValue);
                // added due to https://issues.gradle.org/browse/GRADLE-3005
                properties.put("sonar.junit.reportsPath", testResultsValue);

                project.getPlugins().withType(JacocoPlugin.class, new Action<JacocoPlugin>() {
                    public void execute(JacocoPlugin jacocoPlugin) {
                        JacocoTaskExtension jacocoTaskExtension = testTask.getExtensions().getByType(JacocoTaskExtension.class);
                        File destinationFile = jacocoTaskExtension.getDestinationFile();
                        if (destinationFile.exists()) {
                            properties.put("sonar.jacoco.reportPath", destinationFile);
                        }
                    }
                });
            }
        });

        if (properties.get("sonar.sources") == null) {
            // Should be able to remove this after upgrading to Sonar Runner 2.1 (issue is already marked as fixed),
            // if we can live with the fact that leaf projects w/o source dirs will still cause a failure.
            properties.put("sonar.sources", "");
        }
    }

    private String getProjectKey(Project project) {
        // Sonar uses project keys in URL parameters without internally URL-encoding them.
        // According to my manual tests with sonar-runner plugin based on Sonar Runner 2.0 and Sonar 3.4.1,
        // the current defaults will only cause a problem if project.group or project.name of
        // the Gradle project to which the plugin is applied contains special characters.
        // (':' works, ' ' doesn't.) In such a case, sonar.projectKey can be overridden manually.
        String name = project.getName();
        String group = project.getGroup().toString();
        return group.isEmpty() ? name : group + ":" + name;
    }

    private void addSystemProperties(Map<String, Object> properties) {
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("sonar.")) {
                properties.put(key, entry.getValue());
            }
        }
    }

    private Collection<File> getLibraries(SourceSet main) {
        List<File> libraries = Lists.newLinkedList(Iterables.filter(main.getRuntimeClasspath(), IS_FILE));
        File runtimeJar = Jvm.current().getRuntimeJar();
        if (runtimeJar != null) {
            libraries.add(runtimeJar);
        }

        return libraries;
    }

    private void convertProperties(Map<String, Object> rawProperties, final String projectPrefix, final Map<String, Object> properties) {
        for (Map.Entry<String, Object> entry : rawProperties.entrySet()) {
            String value = convertValue(entry.getValue());
            if (value != null) {
                properties.put(convertKey(entry.getKey(), projectPrefix), value);
            }
        }
    }

    private String convertKey(String key, final String projectPrefix) {
        return projectPrefix.isEmpty() ? key : projectPrefix + "." + key;
    }

    private String convertValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?>) {
            Iterable<String> flattened = Iterables.transform((Iterable<?>) value, new Function<Object, String>() {
                public String apply(Object input) {
                    return convertValue(input);
                }
            });

            Iterable<String> filtered = Iterables.filter(flattened, Predicates.notNull());
            String joined = COMMA_JOINER.join(filtered);
            return joined.isEmpty() ? null : joined;
        } else {
            return value.toString();
        }
    }

    private void addConfiguration(final Project project, final SonarRunnerRootExtension rootExtension) {
        final Configuration configuration = project.getConfigurations().create(SonarRunnerExtension.SONAR_RUNNER_CONFIGURATION_NAME);
        configuration
                .setVisible(false)
                .setTransitive(false)
                .setDescription("The SonarRunner configuration to use to run analysis")
                .getIncoming()
                .beforeResolve(new Action<ResolvableDependencies>() {
                    public void execute(ResolvableDependencies resolvableDependencies) {
                        DependencySet dependencies = resolvableDependencies.getDependencies();
                        if (dependencies.isEmpty()) {
                            String toolVersion = rootExtension.getToolVersion();
                            DependencyHandler dependencyHandler = project.getDependencies();
                            Dependency dependency = dependencyHandler.create("org.codehaus.sonar.runner:sonar-runner-dist:" + toolVersion);
                            configuration.getDependencies().add(dependency);
                        }
                    }
                });
    }

}
