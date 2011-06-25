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
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.util.ConfigureUtil;

public class DefaultScalaSourceSet implements ScalaSourceSet {
    private final SourceDirectorySet scala;
    private final SourceDirectorySet allScala;

    public DefaultScalaSourceSet(String displayName, FileResolver fileResolver) {
        scala = new DefaultSourceDirectorySet(String.format("%s Scala source", displayName), fileResolver);
        scala.getFilter().include("**/*.java", "**/*.scala");
        allScala = new DefaultSourceDirectorySet(String.format("%s Scala source", displayName), fileResolver);
        allScala.getFilter().include("**/*.scala");
        allScala.source(scala);
    }

    public SourceDirectorySet getScala() {
        return scala;
    }

    public ScalaSourceSet scala(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getScala());
        return this;
    }

    public SourceDirectorySet getAllScala() {
        return allScala;
    }
}