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
import org.gradle.api.Project
import org.gradle.api.UnknownDependencyNotation
import org.gradle.api.dependencies.Dependency

/**
* @author Hans Dockter
*/
class TestDependencyImplInteger implements Dependency {
    static final DependencyDescriptor DEPENDENCY_DESCRIPTOR = [:] as DependencyDescriptor
    Set confs
    Object userDependencyDescription
    Project project
    boolean initialized = false


    TestDependencyImplInteger() {
        
    }

    TestDependencyImplInteger(Set confs, Object userDependencyDescription, Project project) {
        if (!(userDependencyDescription instanceof Integer)) {
            throw new UnknownDependencyNotation('somemsg')
        }
        this.confs = confs
        this.userDependencyDescription = userDependencyDescription
        this.project = project
    }

    DependencyDescriptor createDepencencyDescriptor() {
        DEPENDENCY_DESCRIPTOR
    }

    public void initialize() {
        println 'XXXXXXXXXX'
        initialized = true
    }
}
