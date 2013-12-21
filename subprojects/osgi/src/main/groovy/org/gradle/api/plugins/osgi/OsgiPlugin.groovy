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
package org.gradle.api.plugins.osgi


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin

/**
 * A {@link Plugin} which extends the {@link JavaPlugin} to add OSGi meta-information to the project Jars.
 */
public class OsgiPlugin implements Plugin<Project> {
    public void apply(Project project) {
        project.plugins.apply(JavaBasePlugin)

        def osgiConvention = new OsgiPluginConvention(project)
        project.convention.plugins.osgi = osgiConvention

        project.plugins.withType(JavaPlugin) {
            def osgiManifest = osgiConvention.osgiManifest {
                from project.manifest
                classesDir = project.sourceSets.main.output.classesDir
                classpath = project.configurations.runtime
            }
            project.jar.manifest = osgiManifest
        }
    }
}
