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
import org.gradle.api.Task;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.AbstractBuildableModelElement;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetInternal;
import org.gradle.platform.base.ModelInstantiationException;

/**
 * Base class for custom language sourceset implementations. A custom implementation of {@link org.gradle.language.base.LanguageSourceSet} must extend this type.
 */
public abstract class BaseLanguageSourceSet extends AbstractBuildableModelElement implements LanguageSourceSetInternal {
    private String name;
    private String fullName;
    private String parentName;
    private String typeName;
    private SourceDirectorySet source;
    private boolean generated;
    private Task generatorTask;

    // This is here as a convenience for subclasses to create additional SourceDirectorySets
    protected FileResolver fileResolver;

    public String getName() {
        return name;
    }
    public String getParentName() {
        return parentName;
    }

    public String getProjectScopedName() {
        return fullName;
    }

    public String getDisplayName() {
        return String.format("%s '%s:%s'", getTypeName(), parentName, getName());
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    protected String getTypeName() {
        return typeName;
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

    public static <T extends LanguageSourceSet> T create(Class<? extends LanguageSourceSet> publicType, Class<T> type, String name, String parentName, FileResolver fileResolver) {
        if (type.equals(BaseLanguageSourceSet.class)) {
            throw new ModelInstantiationException("Cannot create instance of abstract class BaseLanguageSourceSet.");
        }
        nextSourceSetInfo.set(new SourceSetInfo(name, parentName, publicType.getSimpleName(), fileResolver));
        try {
            try {
                return DirectInstantiator.INSTANCE.newInstance(type);
            } catch (ObjectInstantiationException e) {
                throw new ModelInstantiationException(String.format("Could not create LanguageSourceSet of type %s", publicType.getSimpleName()), e.getCause());
            }
        } finally {
            nextSourceSetInfo.set(null);
        }
    }


    protected BaseLanguageSourceSet() {
        this(nextSourceSetInfo.get());
    }

    private BaseLanguageSourceSet(SourceSetInfo info) {
        if (info == null) {
            throw new ModelInstantiationException("Direct instantiation of a BaseLanguageSourceSet is not permitted. Use a LanguageTypeBuilder instead.");
        }
        this.name = info.name;
        this.parentName = info.parentName;
        this.typeName = info.typeName;
        this.fullName = info.parentName + StringUtils.capitalize(name);
        this.source = new DefaultSourceDirectorySet("source", info.fileResolver);
        this.fileResolver = info.fileResolver;
        super.builtBy(source.getBuildDependencies());
    }

    private static class SourceSetInfo {
        final String name;
        final String parentName;
        final String typeName;
        final FileResolver fileResolver;

        private SourceSetInfo(String name, String parentName, String typeName, FileResolver fileResolver) {
            this.name = name;
            this.parentName = parentName;
            this.typeName = typeName;
            this.fileResolver = fileResolver;
        }
    }
}
