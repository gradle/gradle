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
import org.gradle.api.plugins.BasePluginConvention;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <p>A {@link Plugin} which extends the {@link JavaPlugin} to add OSGi meta-information to the project JARs.</p>
 *
 * @author Hans Dockter
 */
public class OsgiPlugin implements Plugin {
    public void apply(Project project, PluginRegistry pluginRegistry, Map<String, ?> customValues) {
        pluginRegistry.apply(JavaPlugin.class, project, customValues);
        Action<Jar> configureAction = createOsgiConfigureAction();
        project.getTasks().withType(Jar.class).allTasks(configureAction);
    }

    private Action<Jar> createOsgiConfigureAction() {
        return new Action<Jar>() {
            public void execute(final Jar jar) {
                jar.dependsOn(jar.getProject().getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getBuildDependencies());
                jar.defineProperty("osgi", createDefaultOsgiManifest(jar.getProject()));
                jar.doFirst(new TaskAction() {
                    public void execute(Task task) {
                        OsgiManifest osgiManifest = (OsgiManifest) jar.getAdditionalProperties().get("osgi");
                        osgiManifest.setClasspath(getDependencies(osgiManifest,
                                jar.getProject().getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).resolve()));
                        osgiManifest.overwrite(jar.getManifest());
                    }
                });
            }
        };
    }

    private List<File> getDependencies(OsgiManifest osgiManifest, Set<File> dependencies) {
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
        osgiManifest.setName(project.getConvention().getPlugin(BasePluginConvention.class).getArchivesBaseName());
        osgiManifest.setSymbolicName(osgiHelper.getBundleSymbolicName(project));
        return osgiManifest;
    }
}
