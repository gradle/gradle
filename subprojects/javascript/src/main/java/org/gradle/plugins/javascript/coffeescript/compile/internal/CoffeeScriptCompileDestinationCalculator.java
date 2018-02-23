/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.plugins.javascript.coffeescript.compile.internal;

import org.gradle.api.Transformer;
import org.gradle.api.file.RelativePath;

import java.io.File;

public class CoffeeScriptCompileDestinationCalculator implements Transformer<File, RelativePath> {

    private final File destination;

    public CoffeeScriptCompileDestinationCalculator(File destination) {
        this.destination = destination;
    }

    public File transform(RelativePath relativePath) {
        String sourceFileName = relativePath.getLastName();

        String destinationFileNameBase = sourceFileName;
        if (sourceFileName.endsWith(".coffee")) {
            destinationFileNameBase = sourceFileName.substring(0, sourceFileName.length() - 7);
        }

        String destinationFileName = destinationFileNameBase + ".js";
        RelativePath destinationRelativePath = relativePath.replaceLastName(destinationFileName);
        return new File(destination, destinationRelativePath.getPathString());
    }

    public static Transformer<Transformer<File, RelativePath>, File> asFactory() {
        return new Transformer<Transformer<File, RelativePath>, File>() {
            public Transformer<File, RelativePath> transform(File original) {
                return new CoffeeScriptCompileDestinationCalculator(original);
            }
        };
    }
}
