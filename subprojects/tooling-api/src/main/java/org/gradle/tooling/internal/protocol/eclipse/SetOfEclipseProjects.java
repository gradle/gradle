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

package org.gradle.tooling.internal.protocol.eclipse;

import org.gradle.tooling.model.eclipse.EclipseProject;

import java.util.Set;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 2.12
 */
public interface SetOfEclipseProjects {

    /**
     * A flattened set of all projects in the Eclipse workspace.
     * These project models are fully configured, and may be expensive to calculate.
     * Note that not all projects necessarily share the same root.
     */
    Set<EclipseProject> getResult();
}
