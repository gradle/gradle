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

package org.gradle.api.tasks.bundling

import org.gradle.api.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class Jar extends Zip {
    static final String DEFAULT_EXTENSION = 'jar'

    private static Logger logger = LoggerFactory.getLogger(Jar)

    AntJar antJar = new AntJar()

    GradleManifest manifest

    List metaInfResourceCollections

    String fileSetManifest

    Jar self

    Jar(Project project, String name) {
        super(project, name)
        self = this
        extension = DEFAULT_EXTENSION
    }   

    Closure createAntArchiveTask() {
        {-> antJar.execute(new AntMetaArchiveParameter(self.resourceCollections, self.mergeFileSets, self.mergeGroupFileSets, fileSetManifest,
                self.createIfEmpty, self.destinationDir, archiveName, self.manifest, self.metaInfResourceCollections, project.ant))}
    }
}