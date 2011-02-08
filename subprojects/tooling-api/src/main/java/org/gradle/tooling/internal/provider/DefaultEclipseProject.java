/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectVersion1;
import org.gradle.tooling.internal.protocol.eclipse.EclipseSourceDirectoryVersion1;
import org.gradle.util.GUtil;

import java.util.List;

class DefaultEclipseProject implements EclipseProjectVersion1 {
    private final String name;
    private final List<ExternalDependencyVersion1> classpath;
    private final List<EclipseProjectVersion1> children;
    private final List<EclipseSourceDirectoryVersion1> sourceDirectories;

    public DefaultEclipseProject(String name, Iterable<? extends EclipseProjectVersion1> children, Iterable<? extends EclipseSourceDirectoryVersion1> sourceDirectories, Iterable<? extends ExternalDependencyVersion1> classpath) {
        this.name = name;
        this.children = GUtil.addLists(children);
        this.classpath = GUtil.addLists(classpath);
        this.sourceDirectories = GUtil.addLists(sourceDirectories);
    }

    public String getName() {
        return name;
    }

    public List<EclipseProjectVersion1> getChildProjects() {
        return children;
    }

    public Iterable<? extends EclipseSourceDirectoryVersion1> getSourceDirectories() {
        return sourceDirectories;
    }

    public List<ExternalDependencyVersion1> getClasspath() {
        return classpath;
    }
}
