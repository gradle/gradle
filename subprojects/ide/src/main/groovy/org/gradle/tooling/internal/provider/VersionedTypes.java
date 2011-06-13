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

import org.gradle.tooling.internal.*;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion2;

/**
 * @author: Szczepan Faber, created at: 6/11/11
 */
public class VersionedTypes {

    private static final VersionedTypes DEFAULT_VERSION = new VersionedTypes(
            DefaultEclipseProject.class, DefaultEclipseTask.class, DefaultEclipseProjectDependency.class);

    private static final VersionedTypes LINKED_RESOURCES = new VersionedTypes(
            DefaultEclipseProjectVersion4.class, DefaultEclipseTaskVersion2.class, DefaultEclipseProjectDependencyVersion3.class);

    final public Class forProject;
    final public Class forTask;
    final public Class forProjectDependency;

    public VersionedTypes(Class forProject, Class forTask, Class forProjectDependency) {
        this.forProject = forProject;
        this.forTask = forTask;
        this.forProjectDependency = forProjectDependency;
    }

    public static VersionedTypes forType(Class type) {
        if (HierarchicalEclipseProjectVersion2.class.isAssignableFrom(type)) {
            return LINKED_RESOURCES;
        }
        return DEFAULT_VERSION;
    }
}
