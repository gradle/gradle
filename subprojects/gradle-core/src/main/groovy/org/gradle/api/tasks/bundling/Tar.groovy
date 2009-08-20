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
public class Tar extends Zip {
    public static final String TAR_EXTENSION = 'tar'

    Compression compression

    LongFile longFile

    AntTar antTar = new AntTar()

    Tar(Project project, String name) {
        super(project, name)
        extension = TAR_EXTENSION
        compression = Compression.NONE
        longFile = LongFile.WARN
    }

    Closure createAntArchiveTask() {
        {->
            antTar.execute(new AntArchiveParameter(getResourceCollections(), getMergeFileSets(), getMergeGroupFileSets(),
                    getCreateIfEmpty(), getDestinationDir(), getArchiveName(), project.ant), getCompression(), getLongFile())
        }
    }

    TarFileSet tarFileSet(Closure configureClosure) {
        tarFileSet([:], configureClosure)
    }

    TarFileSet tarFileSet(Map args = [:], Closure configureClosure = null) {
        addFileSetInternal(args, TarFileSet, configureClosure)
    }

    public Compression getCompression() {
        return compression;
    }

    public void setCompression(Compression compression) {
        this.compression = compression;
    }

    public LongFile getLongFile() {
        return longFile;
    }

    public void setLongFile(LongFile longFile) {
        this.longFile = longFile;
    }

    public AntTar getAntTar() {
        return antTar;
    }

    public void setAntTar(AntTar antTar) {
        this.antTar = antTar;
    }
}
