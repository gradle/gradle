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

package org.gradle.nativecode.language.cpp.fixtures.app;

import org.gradle.util.GUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class HelloWorldApp {
    public static final String HELLO_WORLD = "Hello, World!";
    public static final String HELLO_WORLD_FRENCH = "Bonjour, Monde!";

    public String getEnglishOutput() {
        return HELLO_WORLD + "\n12";
    }

    public String getFrenchOutput() {
        return HELLO_WORLD_FRENCH + "\n12";
    }

    public String getCustomArgs() {
        return "";
    }

    public String getSourceType() {
        return GUtil.toCamelCase(getMainSource().getPath());
    }

    public abstract SourceFile getMainSource();
    public abstract SourceFile getLibraryHeader();
    public abstract List<SourceFile> getLibrarySources();

    public List<SourceFile> getSourceFiles() {
        ArrayList<SourceFile> sourceFiles = new ArrayList<SourceFile>();
        sourceFiles.add(getMainSource());
        sourceFiles.add(getLibraryHeader());
        sourceFiles.addAll(getLibrarySources());
        return sourceFiles;
    }

    protected SourceFile sourceFile(String path, String name, String content) {
        return new SourceFile(path, name, content);
    }

}
