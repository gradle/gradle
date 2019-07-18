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
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.ScalaSourceSet;

import static org.gradle.api.reflect.TypeOf.typeOf;
import static org.gradle.util.ConfigureUtil.configure;

public class DefaultScalaSourceSet implements ScalaSourceSet, HasPublicType {
    private final SourceDirectorySet scala;
    private final SourceDirectorySet allScala;

    public DefaultScalaSourceSet(String displayName, ObjectFactory objectFactory) {
        scala = objectFactory.sourceDirectorySet("scala", displayName + " Scala source");
        scala.getFilter().include("**/*.java", "**/*.scala");
        allScala = objectFactory.sourceDirectorySet("allscala", displayName + " Scala source");
        allScala.getFilter().include("**/*.scala");
        allScala.source(scala);
    }

    @Override
    public SourceDirectorySet getScala() {
        return scala;
    }

    @Override
    public ScalaSourceSet scala(Closure configureClosure) {
        configure(configureClosure, getScala());
        return this;
    }

    @Override
    public ScalaSourceSet scala(Action<? super SourceDirectorySet> configureAction) {
        configureAction.execute(getScala());
        return this;
    }

    @Override
    public SourceDirectorySet getAllScala() {
        return allScala;
    }

    @Override
    public TypeOf<?> getPublicType() {
        return typeOf(ScalaSourceSet.class);
    }
}
