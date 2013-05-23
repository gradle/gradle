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
package org.gradle.language.jvm.internal;

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Nullable;
import org.gradle.api.Task;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.jvm.ClassDirectoryBinary;
import org.gradle.util.GUtil;

import java.io.File;

public class DefaultClassDirectoryBinary implements ClassDirectoryBinary {
    private final String name;
    private final String baseName;
    private File classesDir;
    private File resourcesDir;
    private final DomainObjectCollection<LanguageSourceSet> source = new DefaultDomainObjectSet<LanguageSourceSet>(LanguageSourceSet.class);
    private Task classesTask;

    public DefaultClassDirectoryBinary(String name) {
        this.name = name;
        this.baseName = removeClassesSuffix(name);
    }

    private String removeClassesSuffix(String name) {
        if (name.endsWith("Classes")) {
            return name.substring(0, name.length() - 7);
        }
        return name;
    }

    public String getName() {
        return name;
    }

    public File getClassesDir() {
        return classesDir;
    }

    public void setClassesDir(File classesDir) {
        this.classesDir = classesDir;
    }

    public File getResourcesDir() {
        return resourcesDir;
    }

    public void setResourcesDir(File resourcesDir) {
        this.resourcesDir = resourcesDir;
    }

    public DomainObjectCollection<LanguageSourceSet> getSource() {
        return source;
    }

    public TaskDependency getBuildDependencies() {
        return null;  //TODO
    }

    public Task getClassesTask() {
        return classesTask;
    }

    public void setClassesTask(Task classesTask) {
        this.classesTask = classesTask;
    }

    public String getTaskName(@Nullable String verb, @Nullable String target) {
        if (verb == null && target == null) {
            return GUtil.toLowerCamelCase(baseName);
        }
        if (verb == null) {
            return GUtil.toLowerCamelCase(String.format("%s %s", getTaskBaseName(), target));
        }
        if (target == null) {
            return GUtil.toLowerCamelCase(String.format("%s %s", verb, baseName));
        }
        return GUtil.toLowerCamelCase(String.format("%s %s %s", verb, getTaskBaseName(), target));
    }

    private String getTaskBaseName() {
        return baseName.equals("main") ? "" : baseName;
    }

    public String toString() {
        return String.format("classes '%s'", baseName);
    }
}
