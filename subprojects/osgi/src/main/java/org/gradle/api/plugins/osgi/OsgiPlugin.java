/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.internal.Factory;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.SingleMessageLogger;

import java.io.File;

/**
 * A {@link Plugin} which extends the {@link JavaPlugin} to add OSGi meta-information to the project Jars.
 */
@Deprecated
public class OsgiPlugin implements Plugin<Project> {
    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(JavaBasePlugin.class);

        SingleMessageLogger.nagUserOfPluginReplacedWithExternalOne("osgi", "biz.aQute.bnd");
        final OsgiPluginConvention osgiConvention = DeprecationLogger.whileDisabled(new Factory<OsgiPluginConvention>() {
            @Override
            public OsgiPluginConvention create() {
                return new OsgiPluginConvention((ProjectInternal) project);
            }
        });
        project.getConvention().getPlugins().put("osgi", osgiConvention);

        project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
            @Override
            public void execute(JavaPlugin javaPlugin) {

                // When creating the OSGi manifest, we must have a single view of all of the classes included in the jar.
                Sync prepareOsgiClasses = project.getTasks().create("osgiClasses", Sync.class);
                FileCollection classes = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName("main").getOutput().getClassesDirs();
                File singleClassesDirectory = new File(project.getBuildDir(), "osgi-classes");
                prepareOsgiClasses.setDescription("Prepares a single classes directory required for OSGi analysis.");
                prepareOsgiClasses.from(classes);
                prepareOsgiClasses.into(singleClassesDirectory);

                Jar jarTask = (Jar) project.getTasks().getByName("jar");
                jarTask.dependsOn(prepareOsgiClasses);
                OsgiManifest osgiManifest = osgiConvention.osgiManifest();
                osgiManifest.setClassesDir(singleClassesDirectory);
                osgiManifest.setClasspath(project.getConfigurations().getByName("runtime"));

                jarTask.setManifest(osgiManifest);
            }
        });
    }
}
