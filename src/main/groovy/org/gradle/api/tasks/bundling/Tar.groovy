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
import org.gradle.api.tasks.util.TarFileSet

/**
 * @author Hans Dockter
 */
class Tar extends Zip {
    static final String TAR_EXTENSION = 'tar'

    Tar self

    Compression compression

    LongFile longFile

    AntTar antTar = new AntTar()

    Tar(Project project, String name) {
        super(project, name)
        self = this
        extension = TAR_EXTENSION
        compression = Compression.NONE
        longFile = LongFile.WARN
    }

    String getExtension() {
        super.getExtension() + compression.extension
    }

    Closure createAntArchiveTask() {
        {->
            antTar.execute(new AntArchiveParameter(self.resourceCollections, self.mergeFileSets, self.mergeGroupFileSets,
                    self.createIfEmpty, self.destinationDir, archiveName, project.ant), self.compression, self.longFile)
        }
    }

    TarFileSet tarFileSet(Closure configureClosure) {
        tarFileSet([:], configureClosure)
    }

    TarFileSet tarFileSet(Map args = [:], Closure configureClosure = null) {
        createFileSetInternal(args, TarFileSet, configureClosure)
    }
}
