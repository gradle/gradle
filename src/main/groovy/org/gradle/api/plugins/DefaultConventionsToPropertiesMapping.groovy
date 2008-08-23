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

import org.gradle.api.tasks.bundling.GradleManifest
import org.gradle.api.tasks.util.FileSet

/**
 * @author Hans Dockter
 */
class DefaultConventionsToPropertiesMapping {
    public final static Map CLEAN = [
            dir: {it.project.buildDir}
    ]
    public final static Map JAVADOC = [
            srcDirs: {it.plugins.java.srcDirs},
            destinationDir: {it.plugins.java.javadocDir},
            dependencyManager: {it.plugins.java.project.dependencies}
    ]
    public final static Map RESOURCES = [
            destinationDir: {it.plugins.java.classesDir},
            srcDirs: {it.plugins.java.resourceDirs}
    ]
    public final static Map COMPILE = [
            destinationDir: {it.plugins.java.classesDir},
            srcDirs: {it.plugins.java.srcDirs},
            sourceCompatibility: {it.plugins.java.sourceCompatibility},
            targetCompatibility: {it.plugins.java.targetCompatibility},
            dependencyManager: {it.plugins.java.project.dependencies}
    ]
    public final static Map TEST_RESOURCES = [
            destinationDir: {it.plugins.java.testClassesDir},
            srcDirs: {it.plugins.java.testResourceDirs}
    ]
    public final static Map TEST_COMPILE = [
            destinationDir: {it.plugins.java.testClassesDir},
            srcDirs: {it.plugins.java.testSrcDirs},
            sourceCompatibility: {it.plugins.java.sourceCompatibility},
            targetCompatibility: {it.plugins.java.targetCompatibility},
            unmanagedClasspath: {[it.plugins.java.classesDir]},
            dependencyManager: {it.project.dependencies}
    ]
    public final static Map TEST = [
            testClassesDir: {it.plugins.java.testClassesDir},
            testResultsDir: {it.plugins.java.testResultsDir},
            testReportDir: {it.plugins.java.testReportDir},
//            // Order of dirs is important because of classpath!
//            unmanagedClasspath: {[it.plugins.java.classesDir]},
            dependencyManager: {it.project.dependencies}
    ]
    private final static Map ARCHIVE = [
            destinationDir: {it.project.buildDir},
            dependencyManager: {it.project.dependencies},
            version: {"${it.project.version}"}
    ]
    public final static Map ZIP = ARCHIVE + [
            destinationDir: {it.plugins.java.distsDir},
            configurations: {[JavaPlugin.DISTS] as String[]}
    ]
    public final static Map TAR = ZIP
    public final static Map JAR = ARCHIVE + [
            baseDir: {it.plugins.java.classesDir},
            configurations: {[JavaPlugin.LIBS] as String[]},
            manifest: {new GradleManifest(it.plugins.java.manifest.manifest)},
            metaInfResourceCollections: {it.plugins.java.metaInf}
    ]

    public final static Map WAR = JAR.subMap(JAR.keySet() - 'baseDir') + [
            libConfigurations: {[JavaPlugin.RUNTIME]},
            libExcludeConfigurations: {[WarPlugin.PROVIDED_RUNTIME]},
            resourceCollections: {[new FileSet(it.plugins.java.webAppDir)]},
            classesFileSets: {[new FileSet(it.plugins.java.classesDir)]}
    ]
    
    public final static Map LIB = [
            defaultArchiveTypes: {it.plugins.java.archiveTypes}
    ]
    public final static Map DIST = [
            defaultArchiveTypes: {it.plugins.java.archiveTypes}
    ]
}
