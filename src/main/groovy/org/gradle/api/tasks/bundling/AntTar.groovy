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

import org.gradle.api.tasks.util.FileSet
import org.gradle.api.tasks.util.ZipFileSet
import org.gradle.api.InvalidUserDataException

/**
 * @author Hans Dockter
 */
class AntTar extends AbstractAntArchive {
    void execute(AntArchiveParameter parameter, Compression compression, LongFile longFile) {
        assert parameter
        assert compression
        assert longFile

        Map args = [:]
        args.destfile = "${parameter.destinationDir.absolutePath}/$parameter.archiveName"
        args.compression = compression.antValue
        args.longfile = longFile.antValue

        AntBuilder ant = parameter.ant

        List unpackedMergeGroupFileSets = []
        parameter.mergeGroupFileSets.each {FileSet fileSet ->
            File tmpDir = File.createTempFile('gradle_', 'tarMergeGroup')
            tmpDir.delete()
            tmpDir.mkdirs()
            ant.copy(todir: tmpDir) {
                fileSet.addToAntBuilder(delegate)
            }
            tmpDir.listFiles().each { File file ->
                if (file.isDirectory()) {
                    throw new InvalidUserDataException("A zipfilegroup may not contain directories!")
                }
                unpackedMergeGroupFileSets.add(new ZipFileSet(file))
            }
        }
        ant.tar(args) {
            addResourceCollections(parameter.resourceCollections, delegate)
            addResourceCollections(unpackedMergeGroupFileSets, delegate)
            addMergeFileSets(parameter.mergeFileSets, delegate)
        }
        unpackedMergeGroupFileSets.each {FileSet fileSet ->
            ant.delete(dir: fileSet.dir)
        }

    }
}