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

import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.DependencyManager
import org.gradle.api.internal.dependencies.DependenciesUtil

/**
 * @author Hans Dockter
 */
class DefaultGradleArtifact implements GradleArtifact {
    String userDescription

    

    DefaultGradleArtifact(String userDescription) {
        this.userDescription = userDescription
    }

    Artifact createIvyArtifact(ModuleRevisionId moduleRevisionId) {
        Map groups = DependenciesUtil.splitExtension(userDescription)
        List coreSplit = groups.core.split(':')
        String baseName = coreSplit[0]
        Map extraAttributes = coreSplit.size() > 1 ? [(DependencyManager.CLASSIFIER): coreSplit[1]] : [:]
        new DefaultArtifact(moduleRevisionId, null, baseName, groups.extension, groups.extension, extraAttributes)
    }

    String toString() {
        "Artifact type=${userDescription.class} value=$userDescription"
    }


}
