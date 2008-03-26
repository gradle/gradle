/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.*
import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */

class JavaConvention {
    static final Map DEFAULT_ARCHIVE_TYPES = [
            jar: new ArchiveType("jar", DefaultConventionsToPropertiesMapping.JAR, Jar),
            zip: new ArchiveType("zip", DefaultConventionsToPropertiesMapping.ZIP, Zip),
            war: new ArchiveType("war", DefaultConventionsToPropertiesMapping.WAR, War),
            tar: new ArchiveType("tar", DefaultConventionsToPropertiesMapping.TAR, Tar),
            'tar.gz': new ArchiveType("tar.gz", DefaultConventionsToPropertiesMapping.TAR, Tar),
            'tar.bzip2': new ArchiveType("tar.bzip2", DefaultConventionsToPropertiesMapping.TAR, Tar)
    ]

    Project project

    File srcRoot
    List srcDirs = []
    List resourceDirs = []
    File classesDir
    List testSrcDirs = []
    List testResourceDirs = []
    File testClassesDir
    File testResultsDir
    File distDir
    String sourceCompatibility
    String targetCompatibility

    Map archiveTypes
    GradleManifest manifest
    FileSet metaInf

    JavaConvention(Project project) {
        this.project = project
        manifest = new GradleManifest()
        metaInf = new FileSet()
        srcRoot = project.file('src')
        classesDir = new File(project.buildDir, 'classes')
        testClassesDir = new File(project.buildDir, 'test-classes')
        distDir = new File(project.buildDir, 'distributions')
        srcDirs << new File(srcRoot, 'main/java')
        resourceDirs << new File(srcRoot, 'main/resources')
        testSrcDirs << new File(srcRoot, 'test/java')
        testResourceDirs << new File(srcRoot, 'test/resources')
        testResultsDir = new File(project.buildDir, 'test-results')
        archiveTypes = DEFAULT_ARCHIVE_TYPES
    }

    File mkdir(File parent = null, String name) {
        if (!name) {throw new InvalidUserDataException('You must specify the name of the directory')}
        File baseDir = parent ?: project.buildDir
        File result = new File(baseDir, name)
        result.mkdirs()
        result
    }
}
