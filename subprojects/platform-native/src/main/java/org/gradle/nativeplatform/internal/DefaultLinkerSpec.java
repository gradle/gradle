/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.internal;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DefaultLinkerSpec extends AbstractBinaryToolSpec implements LinkerSpec {

    private final List<File> objectFiles = new ArrayList<File>();
    private final List<File> libraries = new ArrayList<File>();
    private final List<File> libraryPath = new ArrayList<File>();
    private File outputFile;

    @Override
    public List<File> getObjectFiles() {
        return objectFiles;
    }

    @Override
    public void objectFiles(Iterable<File> objectFiles) {
        addAll(this.objectFiles, objectFiles);
    }

    @Override
    public List<File> getLibraries() {
        return libraries;
    }

    @Override
    public void libraries(Iterable<File> libraries) {
        addAll(this.libraries, libraries);
    }

    @Override
    public List<File> getLibraryPath() {
        return libraryPath;
    }

    @Override
    public void libraryPath(File... libraryPath) {
        Collections.addAll(this.libraryPath, libraryPath);
    }

    @Override
    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }

    private void addAll(List<File> list, Iterable<File> iterable) {
        for (File file : iterable) {
            list.add(file);
        }
    }
}
