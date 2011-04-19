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
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.util.ConfigureUtil;

public class DefaultGroovySourceSet implements GroovySourceSet {
    private final SourceDirectorySet groovy;
    private final SourceDirectorySet allGroovy;

    public DefaultGroovySourceSet(String displayName, FileResolver fileResolver) {
        groovy = new DefaultSourceDirectorySet(String.format("%s Groovy source", displayName), fileResolver);
        groovy.getFilter().include("**/*.java", "**/*.groovy");
        allGroovy = new DefaultSourceDirectorySet(String.format("%s Groovy source", displayName), fileResolver);
        allGroovy.source(groovy);
        allGroovy.getFilter().include("**/*.groovy");
    }

    public SourceDirectorySet getGroovy() {
        return groovy;
    }

    public GroovySourceSet groovy(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getGroovy());
        return this;
    }

    public SourceDirectorySet getAllGroovy() {
        return allGroovy;
    }
}
