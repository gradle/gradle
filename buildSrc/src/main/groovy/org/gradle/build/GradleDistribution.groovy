/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.build

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity

@CompileStatic
class GradleDistribution {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    ConfigurableFileTree staticContent

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    ConfigurableFileTree samples

    @Classpath
    SortedSet<File> core

    @Classpath
    SortedSet<File> plugins

    GradleDistribution(Project project, File gradleHome) {
        staticContent  = project.fileTree(gradleHome)
        staticContent.exclude 'lib/**'
        staticContent.exclude 'samples/**'
        staticContent.exclude 'src/**'
        staticContent.exclude 'docs/**'
        samples = project.fileTree(new File(gradleHome, 'samples'))
        def libDir = project.fileTree(new File(gradleHome, 'lib'))
        libDir.include('*.jar')
        libDir.exclude('plugins/**')
        core = libDir.files as SortedSet
        def pluginsDir = project.fileTree(new File(gradleHome, 'lib/plugins'))
        pluginsDir.include('*.jar')
        plugins = pluginsDir.files as SortedSet
    }
}
