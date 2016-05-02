/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.javascript;

import org.gradle.api.Transformer;
import org.gradle.api.internal.file.RelativeFile;

import java.io.File;

public class JavaScriptCompileDestinationCalculator implements Transformer<File, RelativeFile> {
    private final File destinationDir;

    public JavaScriptCompileDestinationCalculator(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    @Override
    public File transform(RelativeFile file) {
        final File outputFileDir = new File(destinationDir, file.getRelativePath().getParent().getPathString());
        return new File(outputFileDir, getMinifiedFileName(file.getFile().getName()));
    }

    private static String getMinifiedFileName(String fileName) {
        int extIndex = fileName.lastIndexOf('.');
        if (extIndex == -1) {
            return fileName + ".min";
        }
        String prefix = fileName.substring(0, extIndex);
        String extension = fileName.substring(extIndex);
        return prefix + ".min" + extension;
    }
}
