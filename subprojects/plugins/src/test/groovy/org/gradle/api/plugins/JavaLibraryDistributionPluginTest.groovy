/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip
import org.gradle.util.HelperUtil
import spock.lang.Specification

class JavaLibraryDistributionPluginTest extends Specification {
    private final Project project = HelperUtil.createRootProject();
    private final JavaLibraryDistributionPlugin plugin = new JavaLibraryDistributionPlugin();

    def "applies JavaPlugin and adds convention object with default values"() {
        when:
        plugin.apply(project)

        then:
        project.plugins.hasPlugin(JavaPlugin.class)
        project.extensions.getByType(DistributionExtension.class) != null
        project.distribution.name == project.name

    }



    def "adds distZip task to project"() {
        when:
        plugin.apply(project)

        then:
        def task = project.tasks[JavaLibraryDistributionPlugin.TASK_DIST_ZIP_NAME]
        task instanceof Zip
        task.archiveName == "${project.distribution.name}.zip"
    }

    public void "distribution name is configurable"() {
        when:
        plugin.apply(project)
        project.distribution.name = "SuperApp";

        then:
        def distZipTask = project.tasks[JavaLibraryDistributionPlugin.TASK_DIST_ZIP_NAME]
        distZipTask.archiveName == "SuperApp.zip"
    }
	
    
}
