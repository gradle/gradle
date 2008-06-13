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
import org.gradle.api.internal.dependencies.DependenciesUtil
import org.gradle.api.DependencyManager
import org.gradle.api.internal.dependencies.DependencyDescriptorFactory

/**
 * @author Hans Dockter
 */
class ModuleDependency extends AbstractDependency {
    boolean force = false

    List excludeRules = []

    DependencyDescriptorFactory dependencyDescriptorFactory = new DependencyDescriptorFactory()

    ModuleDependency(Object userDependencyDescription) {
        super(null, userDependencyDescription, null)
    }

    ModuleDependency(Set confs, Object userDependencyDescription, DefaultProject project) {
        super(confs, userDependencyDescription, project)
    }

    boolean isValidDescription(Object userDependencyDescription) {
        if (DependenciesUtil.hasExtension(userDependencyDescription)) { return false }
        int elementCount = (userDependencyDescription as String).split(':').size()
        return (elementCount == 3 || elementCount == 4)
    }

    Class[] userDepencencyDescriptionType() {
        [String, GString]
    }

    DependencyDescriptor createDepencencyDescriptor() {
        dependencyDescriptorFactory.createDescriptor((String) userDependencyDescription, force, true, false, confs, excludeRules)
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
