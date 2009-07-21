/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.quality;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.plugins.*;

import java.util.Set;

/**
 * A {@link Plugin} which measures and enforces code quality for Java projects.
 */
public class CodeQualityPlugin implements Plugin {
    public static String CHECKSTYLE_TASK = "checkstyle";
    public static String CHECKSTYLE_TESTS_TASK = "checkstyleTests";
    public static String CODE_NARC_TASK = "codenarc";
    public static String CODE_NARC_TESTS_TASK = "codenarcTests";
    public static String CHECK_TASK = "check";

    public void use(final Project project, ProjectPluginsContainer projectPluginsHandler) {
        projectPluginsHandler.usePlugin(ReportingBasePlugin.class, project);

        Task task = project.getTasks().add(CHECK_TASK);
        task.setDescription("Executes all quality checks");

        project.getPlugins().withType(JavaPlugin.class).allPlugins(new Action<Plugin>() {
            public void execute(Plugin plugin) {
                configureForJavaPlugin(project);
            }
        });
        project.getPlugins().withType(GroovyPlugin.class).allPlugins(new Action<Plugin>() {
            public void execute(Plugin plugin) {
                configureForGroovyPlugin(project);
            }
        });
    }

    private void configureForJavaPlugin(final Project project) {
        project.getConvention().getPlugins().put("javaCodeQuality", new JavaCodeQualityPluginConvention(project));

        project.getTasks().getByName(CHECK_TASK).dependsOn(new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                return project.getTasks().withType(Checkstyle.class).getAll();
            }
        });

        project.getTasks().withType(Checkstyle.class).allTasks(new Action<Checkstyle>() {
            public void execute(Checkstyle checkstyle) {
                checkstyle.conventionMapping("srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(JavaPluginConvention.class).getSrcDirs();
                    }
                });
                checkstyle.conventionMapping("configFile", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleConfigFile();
                    }
                });
            }
        });

        Checkstyle checkstyle = project.getTasks().add(CHECKSTYLE_TASK, Checkstyle.class);
        checkstyle.setDescription("Runs Checkstyle against the Java source code.");
        checkstyle.conventionMapping("resultFile", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleResultFile();
            }
        });

        checkstyle = project.getTasks().add(CHECKSTYLE_TESTS_TASK, Checkstyle.class);
        checkstyle.setDescription("Runs Checkstyle against the Java test source code.");
        checkstyle.conventionMapping("srcDirs", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(JavaPluginConvention.class).getTestSrcDirs();
            }
        });
        checkstyle.conventionMapping("configFile", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleTestConfigFile();
            }
        });
        checkstyle.conventionMapping("resultFile", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(JavaCodeQualityPluginConvention.class).getCheckstyleTestResultFile();
            }
        });

        project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME).dependsOn(CHECK_TASK);
    }

    private void configureForGroovyPlugin(final Project project) {
        project.getConvention().getPlugins().put("groovyCodeQuality", new GroovyCodeQualityPluginConvention(project));

        project.getTasks().getByName(CHECK_TASK).dependsOn(new TaskDependency() {
            public Set<? extends Task> getDependencies(Task task) {
                return project.getTasks().withType(CodeNarc.class).getAll();
            }
        });

        project.getTasks().withType(CodeNarc.class).allTasks(new Action<CodeNarc>() {
            public void execute(CodeNarc codeNarc) {
                codeNarc.conventionMapping("srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(GroovyPluginConvention.class).getGroovySrcDirs();
                    }
                });
                codeNarc.conventionMapping("configFile", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcConfigFile();
                    }
                });
            }
        });

        CodeNarc codeNarc = project.getTasks().add(CODE_NARC_TASK, CodeNarc.class);
        codeNarc.setDescription("Runs CodeNarc against the Groovy source code.");
        codeNarc.conventionMapping("reportFile", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcReportFile();
            }
        });

        codeNarc = project.getTasks().add(CODE_NARC_TESTS_TASK, CodeNarc.class);
        codeNarc.setDescription("Runs CodeNarc against the Groovy test source code.");
        codeNarc.conventionMapping("srcDirs", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(GroovyPluginConvention.class).getGroovyTestSrcDirs();
            }
        });
        codeNarc.conventionMapping("configFile", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcTestConfigFile();
            }
        });
        codeNarc.conventionMapping("reportFile", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return convention.getPlugin(GroovyCodeQualityPluginConvention.class).getCodeNarcTestReportFile();
            }
        });
    }
}
