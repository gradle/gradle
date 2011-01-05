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


import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.util.HelperUtil
import spock.lang.Specification
import org.gradle.api.tasks.SourceSet

public class OsgiPluginTest extends Specification {
    private final Project project = HelperUtil.createRootProject();
    private final OsgiPlugin osgiPlugin = new OsgiPlugin();
    
    public void appliesTheJavaPlugin() {
        osgiPlugin.apply(project);

        expect:
        project.plugins.hasPlugin('java-base')
        project.convention.plugins.osgi instanceof OsgiPluginConvention
    }

    public void addsAnOsgiManifestToTheDefaultJar() {
        project.apply(plugin: 'java')
        osgiPlugin.apply(project);
        
        expect:
        OsgiManifest osgiManifest = project.jar.manifest
        osgiManifest.mergeSpecs[0].mergePaths[0] == project.manifest
        osgiManifest.classpath == project.configurations."$JavaPlugin.RUNTIME_CONFIGURATION_NAME"
        osgiManifest.classesDir == project.sourceSets."$SourceSet.MAIN_SOURCE_SET_NAME".classesDir
    }
}
