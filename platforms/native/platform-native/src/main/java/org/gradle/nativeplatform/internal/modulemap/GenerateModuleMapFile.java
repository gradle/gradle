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

package org.gradle.nativeplatform.internal.modulemap;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.internal.CollectionUtils.collect;
import static org.gradle.util.internal.CollectionUtils.filter;

public class GenerateModuleMapFile {
    public static void generateFile(File moduleMapFile, String moduleName, List<String> publicHeaderDirs) {
        String firstLine = "module " + moduleName + " {";
        List<String> lines = new ArrayList<>();
        lines.add(firstLine);
        List<String> validHeaderDirs = filter(publicHeaderDirs, path -> new File(path).exists());
        lines.addAll(collect(validHeaderDirs, path -> "\tumbrella \"" + path + "\""));
        lines.add("\texport *");
        lines.add("}");
        try {
            Files.createParentDirs(moduleMapFile);
            FileUtils.writeLines(moduleMapFile, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not generate a module map for " + moduleName, e);
        }
    }
}
