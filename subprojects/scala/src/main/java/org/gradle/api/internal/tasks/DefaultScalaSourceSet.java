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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.ScalaSourceDirectorySet;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.util.internal.ConfigureUtil.configure;

@Deprecated
public class DefaultScalaSourceSet implements org.gradle.api.tasks.ScalaSourceSet, HasPublicType {
    private final ScalaSourceDirectorySet scala;
    private final SourceDirectorySet allScala;

    public DefaultScalaSourceSet(String displayName, ObjectFactory objectFactory) {
        scala = createScalaSourceDirectorySet("scala", displayName + " Scala source", objectFactory);
        allScala = objectFactory.sourceDirectorySet("allscala", displayName + " Scala source");
        allScala.getFilter().include("**/*.scala");
        allScala.source(scala);
    }

    private static ScalaSourceDirectorySet createScalaSourceDirectorySet(String name, String displayName, ObjectFactory objectFactory) {
        ScalaSourceDirectorySet scalaSourceDirectorySet = new DefaultScalaSourceDirectorySet(objectFactory.sourceDirectorySet(name, displayName));
        scalaSourceDirectorySet.getFilter().include("**/*.java", "**/*.scala");
        return scalaSourceDirectorySet;
    }

    @Override
    public ScalaSourceDirectorySet getScala() {
        return scala;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public org.gradle.api.tasks.ScalaSourceSet scala(Closure configureClosure) {
        configure(configureClosure, getScala());
        return this;
    }

    @Override
    public org.gradle.api.tasks.ScalaSourceSet scala(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getScala());
        return this;
    }

    @Override
    public SourceDirectorySet getAllScala() {
        return allScala;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(org.gradle.api.tasks.ScalaSourceSet.class);
    }
}
