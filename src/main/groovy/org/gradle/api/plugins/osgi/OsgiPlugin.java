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
package org.gradle.api.plugins.osgi;

import org.gradle.api.*;
import org.gradle.api.internal.plugins.osgi.DefaultOsgiManifest;
import org.gradle.api.internal.plugins.osgi.OsgiHelper;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Bundle;
import org.gradle.api.tasks.bundling.Jar;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to add OSGi meta-information to the project JARs.</p> 
 *
 * @author Hans Dockter
 */
public class OsgiPlugin implements Plugin {
    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(JavaPlugin.class, project, customValues);
        Bundle.ConfigureAction configureAction = createOsgiConfigureAction();
        Bundle libsTask = ((Bundle) project.task(JavaPlugin.LIBS));
        for (AbstractArchiveTask abstractArchiveTask : libsTask.getArchiveTasks()) {
            configureAction.configure(abstractArchiveTask);
        }
        libsTask.addConfigureAction(createOsgiConfigureAction());
    }

    private Bundle.ConfigureAction createOsgiConfigureAction() {
        return new Bundle.ConfigureAction() {
            public void configure(final AbstractArchiveTask archiveTask) {
                if (archiveTask instanceof Jar) {
                    archiveTask.getProject().addAfterEvaluateListener(new AfterEvaluateListener() {
                        public void afterEvaluate(Project project) {
                            archiveTask.dependsOn(project.getDependencies().configuration(JavaPlugin.RUNTIME).getBuildProjectDependencies());
                        }
                    });
                    archiveTask.defineProperty("osgi", createDefaultOsgiManifest(archiveTask.getProject()));
                    archiveTask.doFirst(new TaskAction() {
                        public void execute(Task task) {
                            Jar jarTask = (Jar) task;
                            OsgiManifest osgiManifest = (OsgiManifest) jarTask.getAdditionalProperties().get("osgi");
                            osgiManifest.setClasspath(getDependencies(
                                    osgiManifest,
                                    jarTask.getProject().getDependencies().configuration(JavaPlugin.RUNTIME).resolve())
                            );
                            osgiManifest.overwrite(jarTask.getManifest());
                        }
                    });
                }
            }
        };
    }

    private List<File> getDependencies(OsgiManifest osgiManifest, List<File> dependencies) {
        ArrayList<File> classpathDependencies = new ArrayList<File>();
        for (File dependency : dependencies) {
            if (isClasspathType(osgiManifest, dependency)) {
                classpathDependencies.add(dependency);
            }
        }
        return classpathDependencies;
    }

    private boolean isClasspathType(OsgiManifest osgiManifest, File dependency) {
        for (String type : osgiManifest.getClasspathTypes()) {
            if (dependency.getName().endsWith(type)) {
                return true;
            }
        }
        return false;
    }

    private OsgiManifest createDefaultOsgiManifest(Project project) {
        OsgiHelper osgiHelper = new OsgiHelper();
        OsgiManifest osgiManifest = new DefaultOsgiManifest();
        osgiManifest.setClassesDir(project.getConvention().getPlugin(JavaPluginConvention.class).getClassesDir());
        osgiManifest.setVersion(osgiHelper.getVersion((String) project.property("version")));
        osgiManifest.setName(project.getArchivesBaseName());
        osgiManifest.setSymbolicName(osgiHelper.getBundleSymbolicName(project));
        return osgiManifest;
    }
}
