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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Plugin;
import static org.gradle.api.plugins.JavaPlugin.*;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.ForkMode;
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
    public static final String GROOVYDOC = "groovydoc";
    static final String GROOVY = "groovy";

    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        JavaPlugin javaPlugin = pluginRegistry.apply(JavaPlugin.class, project, customValues);
        GroovyPluginConvention groovyPluginConvention = new GroovyPluginConvention(project, customValues);
        project.getConvention().getPlugins().put("groovy", groovyPluginConvention);

        configureCompile(javaPlugin, project);

        configureTestCompile(javaPlugin, project);

        configureJavadoc(project);

        configureGroovydoc(project);

        project.getDependencies().addConfiguration(GROOVY).setVisible(false).setTransitive(false);
        project.getDependencies().configuration(COMPILE).extendsFrom(GROOVY);
    }

    private void configureGroovydoc(Project project) {
        Groovydoc groovydoc = (Groovydoc) project.createTask(GUtil.map("type", Groovydoc.class), GROOVYDOC);
        groovydoc.conventionMapping(GUtil.map(
                "srcDirs", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return groovy(convention).getGroovySrcDirs();
            }
        },
                "destinationDir", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return groovy(convention).getGroovydocDir();
            }
        },
                "groovyClasspath", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return task.getProject().getDependencies().configuration("groovy").resolve();
            }
        }));
    }

    private void configureJavadoc(Project project) {
        Javadoc javadoc = (Javadoc) project.task(JAVADOC);
        javadoc.getOptions().exclude("**/*.groovy");
        javadoc.conventionMapping(WrapUtil.<String, ConventionValue>toMap("srcDirs", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return GUtil.addLists(
                        ((JavaPluginConvention) convention.getPlugins().get("java")).getSrcDirs(),
                        groovy(convention).getGroovySrcDirs());
            }
        }));
    }

    private void configureTestCompile(JavaPlugin javaPlugin, Project project) {
        Compile testCompile = javaPlugin.configureTestCompile(
                (Compile) project.createTask(GUtil.map("type", GroovyCompile.class, "dependsOn",
                        TEST_RESOURCES, "overwrite", true), TEST_COMPILE),
                (Compile) project.task(COMPILE),
                DefaultConventionsToPropertiesMapping.TEST_COMPILE);
        testCompile.conventionMapping(GUtil.map(
                "groovySourceDirs", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return groovy(convention).getGroovyTestSrcDirs();
            }
        },
                "groovyClasspath", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return task.getProject().getDependencies().configuration("groovy").resolve();
            }
        }));
    }

    private void configureCompile(JavaPlugin javaPlugin, Project project) {
        Compile compile = javaPlugin.configureCompile(
                (Compile) project.createTask(GUtil.map("type", GroovyCompile.class, "dependsOn", RESOURCES, "overwrite", true), COMPILE),
                DefaultConventionsToPropertiesMapping.COMPILE);
        compile.conventionMapping(GUtil.map(
                "groovySourceDirs", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return groovy(convention).getGroovySrcDirs();
            }
        },
                "groovyClasspath", new ConventionValue() {
            public Object getValue(Convention convention, Task task) {
                return task.getProject().getDependencies().configuration("groovy").resolve();
            }
        }));
    }

    private GroovyPluginConvention groovy(Convention convention) {
        return (GroovyPluginConvention) convention.getPlugins().get("groovy");
    }
}
