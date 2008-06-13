/*
 * Copyright 2007-2008 the original author or authors.
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
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.gradle.api.dependencies.Dependency
import org.gradle.api.DependencyManager

/**
 * @author Hans Dockter
 */
class DependencyDescriptorFactory {
    DependencyDescriptor createDescriptor(String descriptor, boolean force, boolean transitive, boolean changing, Set confs,
                                          List excludeRules, Map extraAttributes = [:]) {
        List dependencyParts = descriptor.split(':')
        Map allExtraAttributes = dependencyParts.size() == 4 ? [(DependencyManager.CLASSIFIER): dependencyParts[3]] : [:]
        allExtraAttributes.putAll(extraAttributes)
        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(null,
                DependenciesUtil.moduleRevisionId(dependencyParts[0], dependencyParts[1], dependencyParts[2], allExtraAttributes),
                force, changing, transitive)
        confs.each { String conf ->
            dd.addDependencyConfiguration(conf, Dependency.DEFAULT_CONFIGURATION)
            excludeRules.each {dd.addExcludeRule(conf, it)}
        }
        dd
    }

}
