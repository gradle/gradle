/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.GroovySourceDirectorySet;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.util.internal.ConfigureUtil.configure;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public abstract class DefaultGroovySourceSet implements org.gradle.api.tasks.GroovySourceSet, HasPublicType {
    private final GroovySourceDirectorySet groovy;
    private final SourceDirectorySet allGroovy;

    @Inject
    public DefaultGroovySourceSet(String name, String displayName, ObjectFactory objectFactory) {
        this.groovy = createGroovySourceDirectorySet(name, displayName, objectFactory);
        allGroovy = objectFactory.sourceDirectorySet("all" + name, displayName + " Groovy source");
        allGroovy.source(groovy);
        allGroovy.getFilter().include("**/*.groovy");
    }

    private static GroovySourceDirectorySet createGroovySourceDirectorySet(String name, String displayName, ObjectFactory objectFactory) {
        GroovySourceDirectorySet groovySourceDirectorySet = objectFactory.newInstance(DefaultGroovySourceDirectorySet.class, objectFactory.sourceDirectorySet(name, displayName + " Groovy source"));
        groovySourceDirectorySet.getFilter().include("**/*.java", "**/*.groovy");
        return groovySourceDirectorySet;
    }

    @Override
    public GroovySourceDirectorySet getGroovy() {
        return groovy;
    }

    @Override
    public org.gradle.api.tasks.GroovySourceSet groovy(@SuppressWarnings("rawtypes") @Nullable Closure configureClosure) {
        configure(configureClosure, getGroovy());
        return this;
    }

    @Override
    public org.gradle.api.tasks.GroovySourceSet groovy(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getGroovy());
        return this;
    }

    @Override
    public SourceDirectorySet getAllGroovy() {
        return allGroovy;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(org.gradle.api.tasks.GroovySourceSet.class);
    }
}
