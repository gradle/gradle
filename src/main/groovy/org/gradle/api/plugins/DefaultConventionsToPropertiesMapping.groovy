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

import org.gradle.api.tasks.util.FileSet
import org.gradle.api.tasks.bundling.GradleManifest

/**
 * @author Hans Dockter
 */
class DefaultConventionsToPropertiesMapping {
    final static Map CLEAN = [
            dir: {it.project.buildDir}
    ]
    final static Map JAVADOC = [
            srcDirs: {it.plugins.java.srcDirs},
            destinationDir: {it.plugins.java.javadocDir}
    ]
    final static Map RESOURCES = [
            destinationDir: {it.plugins.java.classesDir},
            srcDirs: {it.plugins.java.resourceDirs}
    ]
    final static Map COMPILE = [
            destinationDir: {it.plugins.java.classesDir},
            srcDirs: {it.plugins.java.srcDirs},
            sourceCompatibility: {it.plugins.java.sourceCompatibility},
            targetCompatibility: {it.plugins.java.targetCompatibility},
            dependencyManager: {it.plugins.java.project.dependencies}
    ]
    final static Map TEST_RESOURCES = [
            destinationDir: {it.plugins.java.testClassesDir},
            srcDirs: {it.plugins.java.testResourceDirs}
    ]
    final static Map TEST_COMPILE = [
            destinationDir: {it.plugins.java.testClassesDir},
            srcDirs: {it.plugins.java.testSrcDirs},
            sourceCompatibility: {it.plugins.java.sourceCompatibility},
            targetCompatibility: {it.plugins.java.targetCompatibility},
            unmanagedClasspath: {[it.plugins.java.classesDir]},
            dependencyManager: {it.project.dependencies}
    ]
    final static Map TEST = [
            testClassesDir: {it.plugins.java.testClassesDir},
            testResultsDir: {it.plugins.java.testResultsDir},
            // Order of dirs is important because of classpath!
            unmanagedClasspath: {[it.plugins.java.classesDir]},
            dependencyManager: {it.project.dependencies}
    ]
    private final static Map ARCHIVE = [
            destinationDir: {it.project.buildDir},
            dependencyManager: {it.project.dependencies},
            version: {"${it.project.version}"}
    ]
    final static Map ZIP = ARCHIVE + [
            destinationDir: {it.plugins.java.distsDir},
            configurations: {[JavaPlugin.DISTS] as String[]}
    ]
    final static Map TAR = ZIP
    final static Map JAR = ARCHIVE + [
            baseDir: {it.plugins.java.classesDir},
            configurations: {[JavaPlugin.LIBS] as String[]},
            manifest: {new GradleManifest(it.plugins.java.manifest.manifest)},
            metaInfResourceCollections: {it.plugins.java.metaInf},
            resourceCollections: {[new FileSet(it.plugins.java.classesDir)]}
    ]
    // todo Does it really makes sense to add a war to the dists configuration ?
    final static Map WAR = JAR.subMap(JAR.keySet() - 'baseDir') + [
            configurations: {[JavaPlugin.DISTS] as String[]},
            libConfiguration: {JavaPlugin.RUNTIME},
            webInfFileSets: {[new FileSet(it.plugins.java.webAppDir)]},
            classesFileSets: {[new FileSet(it.plugins.java.classesDir)]}
    ]
    final static Map LIB = [
            tasksBaseName: {"${it.project.name}"},
            childrenDependOn: {[JavaPlugin.TEST]},
            defaultArchiveTypes: {it.plugins.java.archiveTypes}
    ]
    final static Map DIST = [
            tasksBaseName: {"${it.project.name}"},
            childrenDependOn: {[JavaPlugin.LIBS]},
            defaultArchiveTypes: {it.plugins.java.archiveTypes}
    ]
}
