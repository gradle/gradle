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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.SourceDirectorySetFactory;
import org.gradle.api.tasks.GosuSourceSet;
import org.gradle.util.ConfigureUtil;

public class DefaultGosuSourceSet implements GosuSourceSet {
    private final SourceDirectorySet gosu;
    private final SourceDirectorySet allGosu;

    public DefaultGosuSourceSet(String displayName, SourceDirectorySetFactory sourceDirectorySetFactory) {
        gosu = sourceDirectorySetFactory.create(String.format("%s Gosu source", displayName));
        gosu.getFilter().include("**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp"); //Gosu does not compile *.java sources
        allGosu = sourceDirectorySetFactory.create(String.format("%s Gosu source", displayName));
        allGosu.getFilter().include("**/*.gs", "**/*.gsx", "**/*.gst", "**/*.gsp");
        allGosu.source(gosu);
    }

    @Override
    public SourceDirectorySet getGosu() {
        return gosu;
    }

    @Override
    public GosuSourceSet gosu(Closure configureClosure) {
        ConfigureUtil.configure(configureClosure, getGosu());
        return this;
    }

    @Override
    public SourceDirectorySet getAllGosu() {
        return allGosu;
    }
}
