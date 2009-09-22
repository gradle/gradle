/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.FileSet;

import java.io.File;

class SingletonFileTree extends AbstractFileTree {
    private final File file;
    private final TaskDependency builtBy;

    public SingletonFileTree(File file, TaskDependency builtBy) {
        this.file = file;
        this.builtBy = builtBy;
    }

    @Override
    public String getDisplayName() {
        return String.format("file '%s'", file);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return builtBy;
    }

    @Override
    public void addToAntBuilder(Object builder, String childNodeName, AntType type) {
        if (file.isDirectory()) {
            new FileSet(file, null).addToAntBuilder(builder, childNodeName, type);
        }
        else if (file.isFile()) {
            super.addToAntBuilder(builder, childNodeName, type);
        }
    }

    public FileTree visit(FileVisitor visitor) {
        if (file.isDirectory()) {
            new FileSet(file, null).visit(visitor);
        }
        else if (file.isFile()) {
            visitor.visitFile(new FileVisitDetails() {
                public File getFile() {
                    return file;
                }

                public RelativePath getRelativePath() {
                    return new RelativePath(true, file.getName());
                }

                public void stopVisiting() {
                }
            });
        }
        return this;
    }
}
