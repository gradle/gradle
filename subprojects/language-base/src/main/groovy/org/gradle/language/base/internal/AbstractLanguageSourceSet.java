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

package org.gradle.language.base.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.language.base.FunctionalSourceSet;

public abstract class AbstractLanguageSourceSet implements LanguageSourceSetInternal {
    private final String name;
    private final String fullName;
    private final String displayName;
    private final SourceDirectorySet source;

    public AbstractLanguageSourceSet(String name, FunctionalSourceSet parent, String typeName, SourceDirectorySet source) {
        this.name = name;
        this.fullName = parent.getName() + StringUtils.capitalize(name);
        this.displayName = String.format("%s '%s:%s'", typeName, parent.getName(), name);
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public void source(Action<? super SourceDirectorySet> config) {
        config.execute(getSource());
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    public TaskDependency getBuildDependencies() {
        return getSource().getBuildDependencies();
    }
}
