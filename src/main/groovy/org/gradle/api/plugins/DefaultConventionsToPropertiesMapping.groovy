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
 * We always cast the closure argument to the JavaConvention object. We try to do this with as less noise as possible.
 * We do this, because we want to use the content assist, reliable refactoring and fail fast.
 *
 * @author Hans Dockter
 */
class DefaultConventionsToPropertiesMapping {
    final static Map CLEAN = [
            dir: {_(it).project.buildDir}
    ]
    final static Map JAVADOC = [
            srcDirs: {_(it).srcDirs},
            destinationDir: {_(it).javadocDir}
    ]
    final static Map RESOURCES = [
            destinationDir: {_(it).classesDir},
            srcDirs: {_(it).resourceDirs}
    ]
    final static Map COMPILE = [
            destinationDir: {_(it).classesDir},
            srcDirs: {_(it).srcDirs},
            sourceCompatibility: {_(it).sourceCompatibility},
            targetCompatibility: {_(it).targetCompatibility},
            dependencyManager: {_(it).project.dependencies}
    ]
    final static Map TEST_RESOURCES = [
            destinationDir: {_(it).testClassesDir},
            srcDirs: {_(it).testResourceDirs}
    ]
    final static Map TEST_COMPILE = [
            destinationDir: {_(it).testClassesDir},
            srcDirs: {_(it).testSrcDirs},
            sourceCompatibility: {_(it).sourceCompatibility},
            targetCompatibility: {_(it).targetCompatibility},
            unmanagedClasspath: {[_(it).classesDir]},
            dependencyManager: {_(it).project.dependencies}
    ]
    final static Map TEST = [
            testClassesDir: {_(it).testClassesDir},
            testResultsDir: {_(it).testResultsDir},
            // Order of dirs is important because of classpath!
            unmanagedClasspath: {[_(it).classesDir]},
            dependencyManager: {_(it).project.dependencies}
    ]
    private final static Map ARCHIVE = [
            destinationDir: {_(it).project.buildDir},
            dependencyManager: {_(it).project.dependencies},
            version: {"${_(it).project.version}"}
    ]
    final static Map ZIP = ARCHIVE + [
            destinationDir: {_(it).distsDir},
            configurations: {[JavaPlugin.DISTS] as String[]}
    ]
    final static Map TAR = ZIP
    final static Map JAR = ARCHIVE + [
            baseDir: {_(it).classesDir},
            configurations: {[JavaPlugin.LIBS] as String[]},
            manifest: {new GradleManifest(_(it).manifest.manifest)},
            metaInfResourceCollections: {_(it).metaInf},
            resourceCollections: {[new FileSet(_(it).classesDir)]}
    ]
    // todo Does it really makes sense to add a war to the dists configuration ?
    final static Map WAR = JAR.subMap(JAR.keySet() - 'baseDir') + [
            configurations: {[JavaPlugin.DISTS] as String[]},
            libConfiguration: {JavaPlugin.RUNTIME},
            webInfFileSets: {[new FileSet(_(it).webAppDir)]},
            classesFileSets: {[new FileSet(_(it).classesDir)]} 
    ]
    final static Map LIB = [
            tasksBaseName: {"${_(it).project.name}"},
            childrenDependOn: {[JavaPlugin.TEST]},
            defaultArchiveTypes: {_(it).archiveTypes}
    ]
    final static Map DIST = [
            tasksBaseName: {"${_(it).project.name}"},
            childrenDependOn: {[JavaPlugin.LIBS]},
            defaultArchiveTypes: {_(it).archiveTypes}
    ]

    static JavaConvention _(def object) {
        object
    }
}
