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

package org.gradle.language.base.sources;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.platform.base.ModelInstantiationException;
import org.gradle.platform.base.internal.ComponentSpecIdentifier;

/**
 * Base class that may be used for custom {@link LanguageSourceSet} implementations. However, it is generally better to use an
 * interface annotated with {@link org.gradle.model.Managed} and not use an implementation class at all.
 */
@Incubating
public class BaseLanguageSourceSet extends AbstractBuildableModelElement implements LanguageSourceSetInternal {
    private final String fullName;
    private final String parentName;
    private final String languageName;
    private final SourceDirectorySet source;
    private boolean generated;
    private Task generatorTask;

    // This is here as a convenience for subclasses to create additional SourceDirectorySets
    protected final SourceDirectorySetFactory sourceDirectorySetFactory;

    public String getParentName() {
        return parentName;
    }

    public String getProjectScopedName() {
        return fullName;
    }

    public String getDisplayName() {
        String languageName = getLanguageName();
        if (languageName.toLowerCase().endsWith("resources")) {
            return String.format("%s '%s:%s'", languageName, parentName, getName());
        }
        return String.format("%s source '%s:%s'", languageName, parentName, getName());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    protected String getLanguageName() {
        return languageName;
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

    public SourceDirectorySet getSource() {
        return source;
    }

    private static ThreadLocal<SourceSetInfo> nextSourceSetInfo = new ThreadLocal<SourceSetInfo>();

    public static <T extends LanguageSourceSet> T create(Class<? extends LanguageSourceSet> publicType, Class<T> implementationType, ComponentSpecIdentifier componentId, String parentName, SourceDirectorySetFactory sourceDirectorySetFactory) {
        nextSourceSetInfo.set(new SourceSetInfo(componentId, publicType, parentName, sourceDirectorySetFactory));
        try {
            try {
                return DirectInstantiator.INSTANCE.newInstance(implementationType);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create LanguageSourceSet of type %s", publicType.getSimpleName()), e.getCause());
            }
        } finally {
            nextSourceSetInfo.set(null);
        }
    }

    public BaseLanguageSourceSet() {
        this(nextSourceSetInfo.get());
    }

    private BaseLanguageSourceSet(SourceSetInfo info) {
        super(validate(info).identifier, info.publicType);
        this.parentName = info.parentName;
        this.fullName = info.parentName + StringUtils.capitalize(getName());
        this.languageName = guessLanguageName(getTypeName());
        this.sourceDirectorySetFactory = info.sourceDirectorySetFactory;
        this.source = sourceDirectorySetFactory.create("source");
        super.builtBy(source.getBuildDependencies());
    }

    private static SourceSetInfo validate(SourceSetInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseLanguageSourceSet is not permitted. Use a @LanguageType rule instead.");
        }
        return info;
    }

    private String guessLanguageName(String typeName) {
        return typeName.replaceAll("LanguageSourceSet$", "").replaceAll("SourceSet$", "").replaceAll("Source$", "").replaceAll("Set$", "");
    }

    private static class SourceSetInfo {
        private final ComponentSpecIdentifier identifier;
        private final Class<? extends LanguageSourceSet> publicType;
        final String parentName;
        final SourceDirectorySetFactory sourceDirectorySetFactory;

        private SourceSetInfo(ComponentSpecIdentifier identifier, Class<? extends LanguageSourceSet> publicType, String parentName, SourceDirectorySetFactory sourceDirectorySetFactory) {
            this.identifier = identifier;
            this.publicType = publicType;
            this.parentName = parentName;
            this.sourceDirectorySetFactory = sourceDirectorySetFactory;
        }
    }
}
