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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.RelativeFile;
import org.gradle.plugins.javascript.coffeescript.CoffeeScriptCompileOptions;
import org.gradle.plugins.javascript.coffeescript.CoffeeScriptCompileSpec;

import java.io.File;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class SerializableCoffeeScriptCompileSpec implements Serializable {

    private final File coffeeScriptJs;
    private final File destinationDir;
    private final List<RelativeFile> source;
    private final CoffeeScriptCompileOptions options;

    public SerializableCoffeeScriptCompileSpec(CoffeeScriptCompileSpec spec) {
        this(spec.getCoffeeScriptJs(), spec.getDestinationDir(), spec.getSource(), spec.getOptions());
    }
    public SerializableCoffeeScriptCompileSpec(File coffeeScriptJs, File destinationDir, FileCollection source, CoffeeScriptCompileOptions options) {
        this.coffeeScriptJs = coffeeScriptJs;
        this.destinationDir = destinationDir;
        this.source = new LinkedList<RelativeFile>();
        this.options = options;

        toRelativeFiles(source, this.source);
    }

    public static void toRelativeFiles(final FileCollection source, final List<RelativeFile> targets) {
        FileTree fileTree = source.getAsFileTree();

        fileTree.visit(new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {}

            public void visitFile(FileVisitDetails fileDetails) {
                targets.add(new RelativeFile(fileDetails.getFile(), fileDetails.getRelativePath()));
            }
        });
    }

    public File getCoffeeScriptJs() {
        return coffeeScriptJs;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public List<RelativeFile> getSource() {
        return source;
    }

    public CoffeeScriptCompileOptions getOptions() {
        return options;
    }
}
