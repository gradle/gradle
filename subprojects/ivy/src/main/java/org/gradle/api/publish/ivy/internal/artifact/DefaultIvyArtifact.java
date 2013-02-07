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

package org.gradle.api.publish.ivy.internal.artifact;

import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.api.tasks.TaskDependency;

import java.io.File;

public class DefaultIvyArtifact implements IvyArtifact {
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final File file;
    private String name;
    private String extension;
    private String type;

    public DefaultIvyArtifact(File file, String name, String extension, String type) {
        this.file = file;
        // TODO:DAZ Validate the name later when actually publishing
        this.name = notNull(name);

        // TODO:DAZ Handle null values in publisher, don't convert here (part of validation story)
        this.extension = nullToEmpty(extension);
        this.type = nullToEmpty(type);
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = notNull(name);
    }
    
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = notNull(type);
    }
    
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = notNull(extension);
    }

    private String nullToEmpty(String input) {
        return input == null ? "" : input;
    }

    private String notNull(String input) {
        if (input == null) {
            throw new IllegalArgumentException();
        }
        return input;
    }

    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }
}
