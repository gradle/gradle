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
package org.gradle.api.plugins.osgi;


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet

/**
 * <p>A   {@link Plugin}   which extends the   {@link JavaPlugin}   to add OSGi meta-information to the project JARs.</p>
 *
 * @author Hans Dockter
 */
public class OsgiPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.getPlugins().apply(JavaBasePlugin.class);

        OsgiPluginConvention osgiConvention = new OsgiPluginConvention(project);
        project.convention.plugins.osgi = osgiConvention

        project.plugins.withType(JavaPlugin.class).allPlugins {javaPlugin ->
            OsgiManifest osgiManifest = osgiConvention.osgiManifest {
                from project.manifest
                classesDir = project.convention.plugins.java.sourceSets."$SourceSet.MAIN_SOURCE_SET_NAME".classesDir
                classpath = project.configurations."$JavaPlugin.RUNTIME_CONFIGURATION_NAME"
            }
            project.jar.manifest = osgiManifest
        }
    }
}
