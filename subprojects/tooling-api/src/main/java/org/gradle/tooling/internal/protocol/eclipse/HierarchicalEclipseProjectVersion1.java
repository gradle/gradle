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
package org.gradle.tooling.internal.protocol.eclipse;

import org.gradle.tooling.internal.protocol.HierarchicalProjectVersion1;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 1.0-milestone-3
 */
public interface HierarchicalEclipseProjectVersion1 extends HierarchicalProjectVersion1 {
    HierarchicalEclipseProjectVersion1 getParent();

    Iterable<? extends HierarchicalEclipseProjectVersion1> getChildren();

    Iterable<? extends EclipseSourceDirectoryVersion1> getSourceDirectories();

    Iterable<? extends EclipseProjectDependencyVersion2> getProjectDependencies();
}
