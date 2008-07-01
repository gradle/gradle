/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.internal.dependencies

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.gradle.api.InvalidUserDataException
import org.gradle.api.dependencies.Dependency
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.UnknownDependencyNotation

/**
* @author Hans Dockter
*/
class TestDependencyImplString implements Dependency {
    static final DependencyDescriptor DEPENDENCY_DESCRIPTOR = [:] as DependencyDescriptor
    Set confs
    Object userDependencyDescription
    DefaultProject project
    boolean initialized = false
    
    TestDependencyImplString(Set confs, Object userDependencyDescription, DefaultProject project) {
        if (!(userDependencyDescription instanceof String)) { throw new UnknownDependencyNotation('somemsg') }
        this.confs = confs
        this.userDependencyDescription = userDependencyDescription
        this.project = project
    }

    DependencyDescriptor createDepencencyDescriptor() {
        DEPENDENCY_DESCRIPTOR
    }

    public void initialize() {
        initialized = true
    }

}
