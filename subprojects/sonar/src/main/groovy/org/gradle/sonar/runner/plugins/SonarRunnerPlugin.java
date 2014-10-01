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
import org.gradle.listener.ActionBroadcast;
import org.gradle.sonar.runner.SonarProperties;
import org.gradle.sonar.runner.SonarRunnerExtension;
import org.gradle.sonar.runner.SonarRunnerRootExtension;
import org.gradle.sonar.runner.tasks.SonarRunner;
import org.gradle.testing.jacoco.plugins.JacocoPlugin;
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.util.CollectionUtils.nonEmptyOrNull;

/**
 * A plugin for analyzing projects with the <a href="http://docs.codehaus.org/display/SONAR/Analyzing+with+SonarQube+Runner">Sonar Runner</a>.
 * <p>
 * When applied to a project, both the project itself and its subprojects will be analyzed (in a single run).
 * <p>
 * Please see the “Sonar Runner Plugin” chapter of the Gradle User Guide for more information.
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

        final Map<Project, ActionBroadcast<SonarProperties>> actionBroadcastMap = Maps.newHashMap();
        SonarRunner sonarRunnerTask = createTask(project, actionBroadcastMap);

        ActionBroadcast<SonarProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, project);
        project.subprojects(new Action<Project>() {
            public void execute(Project project) {
                ActionBroadcast<SonarProperties> actionBroadcast = addBroadcaster(actionBroadcastMap, project);
                project.getExtensions().create(SonarRunnerExtension.SONAR_RUNNER_EXTENSION_NAME, SonarRunnerExtension.class, actionBroadcast);
            }
        });
        SonarRunnerRootExtension rootExtension = project.getExtensions().create(SonarRunnerExtension.SONAR_RUNNER_EXTENSION_NAME, SonarRunnerRootExtension.class, actionBroadcast);
        addConfiguration(project, rootExtension);
        rootExtension.setForkOptions(sonarRunnerTask.getForkOptions());
    }

    private ActionBroadcast<SonarProperties> addBroadcaster(Map<Project, ActionBroadcast<SonarProperties>> actionBroadcastMap, Project project) {
        ActionBroadcast<SonarProperties> actionBroadcast = new ActionBroadcast<SonarProperties>();
        actionBroadcastMap.put(project, actionBroadcast);
        return actionBroadcast;
    }

    private SonarRunner createTask(final Project project, final Map<Project, ActionBroadcast<SonarProperties>> actionBroadcastMap) {
        SonarRunner sonarRunnerTask = project.getTasks().create(SonarRunnerExtension.SONAR_RUNNER_TASK_NAME, SonarRunner.class);
        sonarRunnerTask.setDescription("Analyzes " + project + " and its subprojects with Sonar Runner.");

        ConventionMapping conventionMapping = new DslObject(sonarRunnerTask).getConventionMapping();
        conventionMapping.map("sonarProperties", new Callable<Object>() {
            public Object call() throws Exception {
                Map<String, Object> properties = Maps.newLinkedHashMap();
                computeSonarProperties(project, properties, actionBroadcastMap);
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

    public void computeSonarProperties(Project project, Map<String, Object> properties, Map<Project, ActionBroadcast<SonarProperties>> sonarPropertiesActionBroadcastMap) {
        SonarRunnerExtension extension = project.getExtensions().getByType(SonarRunnerExtension.class);
        if (extension.isSkipProject()) {
            return;
        }

        Map<String, Object> rawProperties = Maps.newLinkedHashMap();
        addGradleDefaults(project, rawProperties);
        evaluateSonarPropertiesBlocks(sonarPropertiesActionBroadcastMap.get(project), rawProperties);
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

        Collections.sort(enabledChildProjects);

        String modules = COMMA_JOINER.join(Iterables.transform(enabledChildProjects, new Function<Project, String>() {
            public String apply(Project input) {
                return input.getName();
            }
        }));

        properties.put(convertKey("sonar.modules", projectPrefix), modules);
        for (Project childProject : enabledChildProjects) {
            computeSonarProperties(childProject, properties, sonarPropertiesActionBroadcastMap);
        }
    }

    private void addGradleDefaults(final Project project, final Map<String, Object> properties) {

        // IMPORTANT: Whenever changing the properties/values here, ensure that the Gradle User Guide chapter on this is still in sync.

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
                List<File> sourceDirectories = nonEmptyOrNull(Iterables.filter(main.getAllSource().getSrcDirs(), FILE_EXISTS));
                properties.put("sonar.sources" , sourceDirectories);
                SourceSet test = javaPluginConvention.getSourceSets().getAt("test");
                List<File> testDirectories = nonEmptyOrNull(Iterables.filter(test.getAllSource().getSrcDirs(), FILE_EXISTS));
                properties.put("sonar.tests", testDirectories);

                properties.put("sonar.binaries", nonEmptyOrNull(Iterables.filter(main.getRuntimeClasspath(), IS_DIRECTORY)));
                properties.put("sonar.libraries", getLibraries(main));

                final Test testTask = (Test) project.getTasks().getByName(JavaPlugin.TEST_TASK_NAME);

                if (sourceDirectories != null || testDirectories != null) {
                    File testResultsDir = testTask.getReports().getJunitXml().getDestination();
                    // create the test results folder to prevent SonarQube from emitting
                    // a warning if a project does not contain any tests
                    testResultsDir.mkdirs();


                    properties.put("sonar.surefire.reportsPath", testResultsDir);
                    // added due to https://issues.gradle.org/browse/GRADLE-3005
                    properties.put("sonar.junit.reportsPath", testResultsDir);
                }

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

    private static void evaluateSonarPropertiesBlocks(ActionBroadcast<? super SonarProperties> propertiesActions, Map<String, Object> properties) {
        SonarProperties sonarProperties = new SonarProperties(properties);
        propertiesActions.execute(sonarProperties);
    }

}
