/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.BuildableModelElement;
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

public abstract class AbstractLanguageSourceSet extends AbstractBuildableModelElement implements LanguageSourceSetInternal {
    private final String fullName;
    private final String parentName;
    private final String languageName;
    private final SourceDirectorySet source;
    private boolean generated;
    private Task generatorTask;

    public AbstractLanguageSourceSet(ComponentSpecIdentifier identifier, Class<? extends BuildableModelElement> publicType, String parentName, SourceDirectorySet source) {
        super(identifier, publicType);
        this.fullName = parentName + StringUtils.capitalize(identifier.getName());
        this.source = source;
        this.parentName = parentName;
        this.languageName = guessLanguageName(getTypeName());
        super.builtBy(source.getBuildDependencies());
    }

    protected String getLanguageName() {
        return languageName;
    }

    private String guessLanguageName(String typeName) {
        return typeName.replaceAll("LanguageSourceSet$", "").replaceAll("SourceSet$", "").replaceAll("Source$", "").replaceAll("Set$", "");
    }

    public String getProjectScopedName() {
        return fullName;
    }

    @Override
    public void builtBy(Object... tasks) {
        generated = true;
        super.builtBy(tasks);
    }

    public void generatedBy(Task generatorTask) {
        this.generatorTask = generatorTask;
    }

    public Task getGeneratorTask() {
        return generatorTask;
    }

    public boolean getMayHaveSources() {
        // TODO:DAZ This doesn't take into account build dependencies of the SourceDirectorySet.
        // Should just ditch SourceDirectorySet from here since it's not really a great model, and drags in too much baggage.
        return generated || !source.isEmpty();
    }

    public String getDisplayName() {
        String languageName = getLanguageName();
        if (languageName.toLowerCase().endsWith("resources")) {
            return String.format("%s '%s:%s'", languageName, parentName, getName());
        }
        return String.format("%s source '%s:%s'", languageName, parentName, getName());
    }

    public SourceDirectorySet getSource() {
        return source;
    }

    @Override
    public String getParentName() {
        return parentName;
    }
}
