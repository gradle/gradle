/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.PluginRegistry;
import static org.gradle.api.plugins.JavaPlugin.*;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.compile.Compile;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.javadoc.Groovydoc;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.util.GUtil;
import org.gradle.util.WrapUtil;

import java.util.Map;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to provide support for compiling and documenting Groovy
 * source files.</p>
 *
 * @author Hans Dockter
 */
public class GroovyPlugin implements Plugin {
    public static final String GROOVYDOC_TASK_NAME = "groovydoc";
    static final String GROOVY_CONFIGURATION_NAME = "groovy";

    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        JavaPlugin javaPlugin = pluginRegistry.apply(JavaPlugin.class, project, customValues);
        GroovyPluginConvention groovyPluginConvention = new GroovyPluginConvention(project, customValues);
        project.getConvention().getPlugins().put("groovy", groovyPluginConvention);

        Configuration groovyConfiguration = project.getConfigurations().add(GROOVY_CONFIGURATION_NAME).setVisible(false).setTransitive(false).
                setDescription("The groovy libraries to be used for this Groovy project.");
        project.getConfigurations().getByName(COMPILE_CONFIGURATION_NAME).extendsFrom(groovyConfiguration);

        configureCompile(project);

        configureTestCompile(javaPlugin, project);

        configureJavadoc(project);

        configureGroovydoc(project);
    }

    private void configureGroovydoc(final Project project) {
        project.getTasks().withType(Groovydoc.class).allTasks(new Action<Groovydoc>() {
            public void execute(Groovydoc groovydoc) {
                groovydoc.setGroovyClasspath(project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME));
                groovydoc.conventionMapping(GUtil.map("srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return groovy(convention).getGroovySrcDirs();
                    }
                }, "destinationDir", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return groovy(convention).getGroovydocDir();
                    }
                }));
            }
        });
        project.getTasks().add(GROOVYDOC_TASK_NAME, Groovydoc.class).setDescription("Generates the groovydoc for the source code.");
    }

    private void configureJavadoc(Project project) {
        Action<Javadoc> taskListener = new Action<Javadoc>() {
            public void execute(Javadoc javadoc) {
                javadoc.exclude("**/*.groovy");
                javadoc.conventionMapping(WrapUtil.<String, ConventionValue>toMap("srcDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return GUtil.addLists(convention.getPlugin(JavaPluginConvention.class).getSrcDirs(), groovy(
                                convention).getGroovySrcDirs());
                    }
                }));
            }
        };
        project.getTasks().withType(Javadoc.class).allTasks(taskListener);
        taskListener.execute((Javadoc) project.task(JAVADOC_TASK_NAME));
    }

    private void configureTestCompile(JavaPlugin javaPlugin, Project project) {
        GroovyCompile compileTests = (GroovyCompile) javaPlugin.configureCompileTests(
                project.getTasks().replace(COMPILE_TESTS_TASK_NAME, GroovyCompile.class),
                (Compile) project.task(COMPILE_TASK_NAME),
                DefaultConventionsToPropertiesMapping.TEST_COMPILE,
                project.getConfigurations());
        compileTests.setGroovyClasspath(project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME));
        compileTests.setDescription("Compiles the Java and Groovy test source code.");
        compileTests.conventionMapping(GUtil.map(
                "groovySourceDirs", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return groovy(convention).getGroovyTestSrcDirs();
            }
        }));
    }

    private void configureCompile(final Project project) {
        project.getTasks().withType(GroovyCompile.class).allTasks(new Action<GroovyCompile>() {
            public void execute(GroovyCompile compile) {
                compile.setGroovyClasspath(project.getConfigurations().getByName(GROOVY_CONFIGURATION_NAME));
                compile.conventionMapping(GUtil.map("groovySourceDirs", new ConventionValue() {
                    public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                        return groovy(convention).getGroovySrcDirs();
                    }
                }));
            }
        });
        project.getTasks().replace(COMPILE_TASK_NAME, GroovyCompile.class).setDescription("Compiles the Java and Groovy source code.");
    }

    private GroovyPluginConvention groovy(Convention convention) {
        return convention.getPlugin(GroovyPluginConvention.class);
    }
}
