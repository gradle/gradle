/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.plugins;

import org.gradle.api.*;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.plugins.ProcessResources;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.util.WrapUtil;

import java.io.File;

/**
 * <p>A {@link org.gradle.api.Plugin} which compiles and tests Java source, and assembles it into a JAR file.</p>
 *
 * @author Hans Dockter
 */
public class JavaBasePlugin implements Plugin<Project> {
    public static final String CHECK_TASK_NAME = "check";
    public static final String BUILD_TASK_NAME = "build";
    public static final String BUILD_DEPENDENTS_TASK_NAME = "buildDependents";
    public static final String BUILD_NEEDED_TASK_NAME = "buildNeeded";
    public static final String VERIFICATION_GROUP = "verification";
    public static final String DOCUMENTATION_GROUP = "documentation";

    public void apply(Project project) {
        project.getPlugins().apply(BasePlugin.class);
        project.getPlugins().apply(ReportingBasePlugin.class);

        JavaPluginConvention javaConvention = new JavaPluginConvention(project);
        project.getConvention().getPlugins().put("java", javaConvention);

        configureCompileDefaults(project, javaConvention);
        configureSourceSetDefaults(javaConvention);

        configureJavaDoc(project);
        configureTest(project);
        configureCheck(project);
        configureBuild(project);
        configureBuildNeeded(project);
        configureBuildDependents(project);
    }

    private void configureSourceSetDefaults(final JavaPluginConvention pluginConvention) {
        pluginConvention.getSourceSets().all(new Action<SourceSet>() {
            public void execute(final SourceSet sourceSet) {
                final Project project = pluginConvention.getProject();
                ConventionMapping conventionMapping = ((IConventionAware) sourceSet).getConventionMapping();

                conventionMapping.map("classesDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        String classesDirName = String.format("classes/%s", sourceSet.getName());
                        return new File(project.getBuildDir(), classesDirName);
                    }
                });
                sourceSet.getJava().srcDir(String.format("src/%s/java", sourceSet.getName()));
                sourceSet.getResources().srcDir(String.format("src/%s/resources", sourceSet.getName()));

                Copy processResources = project.getTasks().add(sourceSet.getProcessResourcesTaskName(), ProcessResources.class);
                processResources.setDescription(String.format("Processes the %s.", sourceSet.getResources()));
                conventionMapping = processResources.getConventionMapping();
                conventionMapping.map("defaultSource", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return sourceSet.getResources();
                    }
                });
                conventionMapping.map("destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return sourceSet.getClassesDir();
                    }
                });

                String compileTaskName = sourceSet.getCompileJavaTaskName();
                Compile compileJava = project.getTasks().add(compileTaskName, Compile.class);
                configureForSourceSet(sourceSet, compileJava);

                Task classes = project.getTasks().add(sourceSet.getClassesTaskName());
                classes.dependsOn(sourceSet.getProcessResourcesTaskName(), compileTaskName);
                classes.setDescription(String.format("Assembles the %s classes.", sourceSet.getName()));
                classes.setGroup(BasePlugin.BUILD_GROUP);

                sourceSet.compiledBy(sourceSet.getClassesTaskName());
            }
        });
    }

    public void configureForSourceSet(final SourceSet sourceSet, AbstractCompile compile) {
        ConventionMapping conventionMapping;
        compile.setDescription(String.format("Compiles the %s.", sourceSet.getJava()));
        conventionMapping = compile.getConventionMapping();
        conventionMapping.map("classpath", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return sourceSet.getCompileClasspath();
            }
        });
        conventionMapping.map("defaultSource", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return sourceSet.getJava();
            }
        });
        conventionMapping.map("destinationDir", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return sourceSet.getClassesDir();
            }
        });
    }

    private void configureCompileDefaults(final Project project, final JavaPluginConvention javaConvention) {
        project.getTasks().withType(AbstractCompile.class, new Action<AbstractCompile>() {
            public void execute(final AbstractCompile compile) {
                ConventionMapping conventionMapping = compile.getConventionMapping();
                conventionMapping.map("sourceCompatibility", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return javaConvention.getSourceCompatibility().toString();
                    }
                });
                conventionMapping.map("targetCompatibility", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return javaConvention.getTargetCompatibility().toString();
                    }
                });
            }
        });
        project.getTasks().withType(Compile.class, new Action<Compile>() {
            public void execute(final Compile compile) {
                ConventionMapping conventionMapping = compile.getConventionMapping();
                conventionMapping.map("dependencyCacheDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return javaConvention.getDependencyCacheDir();
                    }
                });
            }
        });
    }

    private void configureJavaDoc(final Project project) {
        project.getTasks().withType(Javadoc.class, new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.getConventionMapping().map("destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return new File(convention.getPlugin(JavaPluginConvention.class).getDocsDir(), "javadoc");
                    }
                });
                javadoc.getConventionMapping().map("title", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(ReportingBasePluginConvention.class).getApiDocTitle();
                    }
                });
            }
        });
    }

    private void configureCheck(final Project project) {
        Task checkTask = project.getTasks().add(CHECK_TASK_NAME);
        checkTask.setDescription("Runs all checks.");
        checkTask.setGroup(VERIFICATION_GROUP);
    }

    private void configureBuild(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project.");
        buildTask.setGroup(BasePlugin.BUILD_GROUP);
        buildTask.dependsOn(BasePlugin.ASSEMBLE_TASK_NAME);
        buildTask.dependsOn(CHECK_TASK_NAME);
    }

    private void configureBuildNeeded(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_NEEDED_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project and all projects it depends on.");
        buildTask.setGroup(BasePlugin.BUILD_GROUP);
        buildTask.dependsOn(BUILD_TASK_NAME);
    }

    private void configureBuildDependents(Project project) {
        DefaultTask buildTask = project.getTasks().add(BUILD_DEPENDENTS_TASK_NAME, DefaultTask.class);
        buildTask.setDescription("Assembles and tests this project and all projects that depend on it.");
        buildTask.setGroup(BasePlugin.BUILD_GROUP);
        buildTask.dependsOn(BUILD_TASK_NAME);
    }

    private void configureTest(final Project project) {
        project.getTasks().withType(Test.class, new Action<Test>() {
            public void execute(Test test) {
                configureTestDefaults(test, project);
            }
        });
        project.afterEvaluate(new Action<Project>() {
            public void execute(Project project) {
                project.getTasks().withType(Test.class, new Action<Test>() {
                    public void execute(Test test) {
                        overwriteIncludesIfSinglePropertyIsSet(test);
                        overwriteDebugIfDebugPropertyIsSet(test);
                    }
                });
            }
        });
    }

    private void overwriteDebugIfDebugPropertyIsSet(Test test) {
        String debugProp = getTaskPrefixedProperty(test, "debug");
        if (debugProp != null) {
            test.doFirst(new Action<Task>() {
                public void execute(Task task) {
                    task.getLogger().info("Running tests for remote debugging.");
                }
            });
            test.setDebug(true);
        }
    }

    private void overwriteIncludesIfSinglePropertyIsSet(final Test test) {
        String singleTest = getTaskPrefixedProperty(test, "single");
        if (singleTest == null) {
            return;
        }
        test.doFirst(new Action<Task>() {
            public void execute(Task task) {
                test.getLogger().info("Running single tests with pattern: {}", test.getIncludes());
            }
        });
        test.setIncludes(WrapUtil.toSet(String.format("**/%s*.class", singleTest)));
        failIfNoTestIsExecuted(test, singleTest);
    }

    private void failIfNoTestIsExecuted(Test test, final String pattern) {
        test.addTestListener(new TestListener() {
            public void beforeSuite(TestDescriptor suite) {
                // do nothing
            }

            public void afterSuite(TestDescriptor suite, TestResult result) {
                if (suite.getParent() == null && result.getTestCount() == 0) {
                    throw new GradleException("Could not find matching test for pattern: " + pattern);
                }
            }

            public void beforeTest(TestDescriptor testDescriptor) {
                // do nothing
            }

            public void afterTest(TestDescriptor testDescriptor, TestResult result) {
                // do nothing
            }
        });
    }

    private String getTaskPrefixedProperty(Task task, String propertyName) {
        String suffix = '.' + propertyName;
        String value = System.getProperty(task.getPath() + suffix);
        if (value == null) {
            return System.getProperty(task.getName() + suffix);
        }
        return value;
    }

    private void configureTestDefaults(Test test, Project project) {
        test.getConventionMapping().map("testResultsDir", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(JavaPluginConvention.class).getTestResultsDir();
            }
        });
        test.getConventionMapping().map("testReportDir", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(JavaPluginConvention.class).getTestReportDir();
            }
        });
        test.workingDir(project.getProjectDir());
    }
}