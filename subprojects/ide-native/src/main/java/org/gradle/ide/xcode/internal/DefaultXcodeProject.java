/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.ide.xcode.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.ide.xcode.XcodeProject;
import org.gradle.ide.xcode.internal.xcodeproj.GidGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultXcodeProject implements XcodeProject {
    private final GidGenerator gidGenerator = new GidGenerator(Collections.<String>emptySet());
    private final List<File> additionalFiles = new ArrayList<File>();
    private XcodeTarget target;
    private FileCollection sources;
    private File locationDir;

    public void addSourceFile(File sourceFile) {
        additionalFiles.add(sourceFile);
    }

    public FileCollection getSources() {
        return sources;
    }

    public void setSources(FileCollection sources) {
        this.sources = sources;
    }

    public List<File> getSourceFiles() {
        Set<File> allSource = new LinkedHashSet<File>();
        allSource.addAll(additionalFiles);
        allSource.addAll(sources.getFiles());

        return new ArrayList<File>(allSource);
    }

    public XcodeTarget getTarget() {
        return target;
    }

    public void setTarget(XcodeTarget target) {
        this.target = target;
    }

    public File getLocationDir() {
        return locationDir;
    }

    public void setLocationDir(File locationDir) {
        this.locationDir = locationDir;
    }

    public GidGenerator getGidGenerator() {
        return gidGenerator;
    }
}
