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
package org.gradle.language.java.internal;

import org.gradle.api.file.ProjectLayout;
import org.gradle.jvm.Classpath;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.language.java.JavaSourceSet;
import org.gradle.language.jvm.internal.EmptyClasspath;
import org.gradle.platform.base.DependencySpecContainer;
import org.gradle.platform.base.internal.DefaultDependencySpecContainer;

import javax.inject.Inject;

public class DefaultJavaLanguageSourceSet extends BaseLanguageSourceSet implements JavaSourceSet {
    private final Classpath emptyClasspath;
    private final DefaultDependencySpecContainer dependencies = new DefaultDependencySpecContainer();

    @Inject
    public DefaultJavaLanguageSourceSet(ProjectLayout projectLayout) {
        emptyClasspath = new EmptyClasspath(projectLayout);
    }

    @Override
    public Classpath getCompileClasspath() {
        return emptyClasspath;
    }

    @Override
    public DependencySpecContainer getDependencies() {
        return dependencies;
    }
}
