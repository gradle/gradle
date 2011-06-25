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
package org.gradle.tooling.internal;

import org.gradle.tooling.internal.protocol.eclipse.EclipseProjectDependencyVersion3;
import org.gradle.tooling.internal.protocol.eclipse.HierarchicalEclipseProjectVersion2;

import java.io.Serializable;

public class DefaultEclipseProjectDependencyVersion3 implements EclipseProjectDependencyVersion3, Serializable {
    private final String path;
    private final HierarchicalEclipseProjectVersion2 target;

    public DefaultEclipseProjectDependencyVersion3(String path, HierarchicalEclipseProjectVersion2 target) {
        this.target = target;
        this.path = path;
    }

    public HierarchicalEclipseProjectVersion2 getTargetProject() {
        return target;
    }

    public String getPath() {
        return path;
    }

    @Override
    public String toString() {
        return String.format("project dependency %s (%s)", path, target);
    }
}
