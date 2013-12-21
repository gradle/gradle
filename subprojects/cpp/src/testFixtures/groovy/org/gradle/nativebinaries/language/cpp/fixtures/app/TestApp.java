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

package org.gradle.nativebinaries.language.cpp.fixtures.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class TestApp extends TestComponent {
    public abstract SourceFile getMainSource();
    public abstract SourceFile getLibraryHeader();
    public abstract List<SourceFile> getLibrarySources();

    public TestComponent getLibrary() {
        return new TestComponent() {
            @Override
            public List<SourceFile> getSourceFiles() {
                return getLibrarySources();
            }

            @Override
            public List<SourceFile> getHeaderFiles() {
                return Arrays.asList(getLibraryHeader());
            }
        };
    }

    public TestComponent getExecutable() {
        return new TestComponent() {
            @Override
            public List<SourceFile> getHeaderFiles() {
                return Collections.emptyList();
            }

            @Override
            public List<SourceFile> getSourceFiles() {
                return Arrays.asList(getMainSource());
            }
        };
    }

    @Override
    public List<SourceFile> getHeaderFiles() {
        ArrayList<SourceFile> headerFiles = new ArrayList<SourceFile>();
        headerFiles.addAll(getExecutable().getHeaderFiles());
        headerFiles.addAll(getLibrary().getHeaderFiles());
        return headerFiles;
    }

    @Override
    public List<SourceFile> getSourceFiles() {
        ArrayList<SourceFile> sourceFiles = new ArrayList<SourceFile>();
        sourceFiles.addAll(getExecutable().getSourceFiles());
        sourceFiles.addAll(getLibrary().getSourceFiles());
        return sourceFiles;
    }
}
