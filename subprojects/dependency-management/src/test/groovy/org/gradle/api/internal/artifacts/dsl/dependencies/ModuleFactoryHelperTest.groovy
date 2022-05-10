/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.DependencyArtifact
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import spock.lang.Specification

class ModuleFactoryHelperTest extends Specification {

    private static final String MODULE_NAME = 'name'

    private static final DependencyArtifact ARTIFACT =
        new DefaultDependencyArtifact('name', 'type', 'classifier', 'extension', 'url')

    def "adds new artifact and properly sets transitive if type or classifier is set"() {
        given:
        ExternalModuleDependency dep = new DefaultExternalModuleDependency('group', MODULE_NAME, 'version', null)
        initialArtifacts.each { dep.addArtifact(it) }

        when:
        ModuleFactoryHelper.addExplicitArtifactsIfDefined(dep, type, classifier)

        then:
        dep.transitive == transitive
        dep.artifacts == ImmutableSet.copyOf(artifacts)

        where:
        initialArtifacts | type  | classifier | transitive | artifacts
        []               | null  | null       | true       | []
        [ARTIFACT]       | null  | null       | true       | [ARTIFACT]
        []               | 'jar' | null       | false      | [newArtifact('jar', null)]
        [ARTIFACT]       | 'jar' | null       | false      | [ARTIFACT, newArtifact('jar', null)]
        []               | null  | 'class'    | true       | [newArtifact('jar', 'class')]
        [ARTIFACT]       | null  | 'class'    | true       | [ARTIFACT, newArtifact('jar', 'class')]
        []               | 'jar' | 'class'    | false      | [newArtifact('jar', 'class')]
        [ARTIFACT]       | 'jar' | 'class'    | false      | [ARTIFACT, newArtifact('jar', 'class')]
    }

    def newArtifact(String type, String classifier) {
        new DefaultDependencyArtifact(MODULE_NAME, type, type, classifier, null)
    }
}
