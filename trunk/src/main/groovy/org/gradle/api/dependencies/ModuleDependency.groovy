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

package org.gradle.api.dependencies

import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.id.ArtifactId
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import org.apache.ivy.plugins.matcher.PatternMatcher
import org.gradle.api.internal.project.DefaultProject

/**
* @author Hans Dockter
*/
class ModuleDependency extends AbstractDependency {
    boolean force

    List excludeRules = []

    ModuleDependency(Object userDependencyDescription) {
        super(null, userDependencyDescription, null)
    }

    ModuleDependency(Set confs, Object userDependencyDescription, DefaultProject project) {
        super(confs, userDependencyDescription, project)
    }

    boolean isValidDescription(Object userDependencyDescription) {
        if (String.isCase(userDependencyDescription)) {
            return (userDependencyDescription as String).split(':').size() == 3
        }
        true
    }

    Class[] userDepencencyDescriptionType() {
        [String, GString]
    }

    DependencyDescriptor createDepencencyDescriptor() {
        String id = userDependencyDescription
        List dependencyParts = id.split(':')
        DefaultDependencyDescriptor dd = new DefaultDependencyDescriptor(null, createModuleRevisionId(dependencyParts[0], dependencyParts[1], dependencyParts[2]), false, false, true)
        confs.each { String conf ->
            dd.addDependencyConfiguration(conf, Dependency.DEFAULT_CONFIGURATION)
            excludeRules.each {dd.addExcludeRule(conf, it)}
        }
        dd
    }

    ModuleDependency exclude(Map args) {
        String org = args.org ?: PatternMatcher.ANY_EXPRESSION
        String module = args.module ?: PatternMatcher.ANY_EXPRESSION
        excludeRules << new DefaultExcludeRule(new ArtifactId(
                new ModuleId(org, module), PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION,
                PatternMatcher.ANY_EXPRESSION),
                ExactPatternMatcher.INSTANCE, null)
        this
    }


    ModuleDependency force(boolean force) {
        this.force = force
        this
    }

}
