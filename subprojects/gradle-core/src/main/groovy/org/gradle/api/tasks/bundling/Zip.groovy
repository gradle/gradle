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

import org.gradle.api.tasks.util.ZipFileSet

/**
 * @author Hans Dockter
 */
public class Zip extends AbstractArchiveTask {
    public static final String ZIP_EXTENSION = 'zip'

    AntZip antZip = new AntZip()

    Zip() {
        extension = ZIP_EXTENSION
    }

    Closure createAntArchiveTask() {
        { -> antZip.execute(new AntArchiveParameter(getResourceCollections(), getCreateIfEmpty(),
                getDestinationDir(), getArchiveName(), project.ant)) }
    }

    ZipFileSet zipFileSet(Closure configureClosure) {
        zipFileSet([:], configureClosure)
    }

    ZipFileSet zipFileSet(Map args = [:], Closure configureClosure = null) {
        addFileSetInternal(args, ZipFileSet, configureClosure)
    }

    public AntZip getAntZip() {
        return antZip;
    }

    public void setAntZip(AntZip antZip) {
        this.antZip = antZip;
    }
}
