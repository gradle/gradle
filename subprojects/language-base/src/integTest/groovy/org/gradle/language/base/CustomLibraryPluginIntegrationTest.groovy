/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.base

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CustomLibraryPluginIntegrationTest extends AbstractIntegrationSpec {
    def "setup"() {
        buildFile << """
import org.gradle.model.*
import org.gradle.model.collection.*

import javax.inject.Inject
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.runtime.base.internal.DefaultComponentSpecIdentifier

interface SampleLibrary extends LibrarySpec {}

class DefaultSampleLibrary extends DefaultLibrarySpec implements SampleLibrary {
    @Inject
    DefaultSampleLibrary(ComponentSpecIdentifier componentIdentifier){
        super(componentIdentifier)
    }
}
"""
    }

    def "plugin declares custom library"() {
        when:
        buildFile << """
class MySamplePlugin implements Plugin<Project> {

    void apply(final Project project) {

        // This stuff should all happen automatically based on the @ComponentModel annotation
        project.apply(plugin:org.gradle.language.base.plugins.ComponentModelBasePlugin)

        def componentSpecs = project.extensions.getByType(ComponentSpecContainer)
        componentSpecs.registerFactory(SampleLibrary, new NamedDomainObjectFactory<SampleLibrary>() {
            public SampleLibrary create(String name) {
                ComponentSpecIdentifier id = new DefaultComponentSpecIdentifier(project.getPath(), name);
                return new DefaultSampleLibrary(id);
            }
        });
    }

    @RuleSource
    static class Rules {
        @Mutate
        void createSampleLibraryComponents(NamedItemCollectionBuilder<ComponentSpec> componentSpecs, ProjectIdentifier projectIdentifier) {
            componentSpecs.create("sampleLib", SampleLibrary)
        }

        @Mutate
        void closeComponentsForTasks(NamedItemCollectionBuilder<Task> tasks, ComponentSpecContainer components) {}
    }
}
apply plugin:MySamplePlugin

task checkModel << {
    assert project.projectComponents.size() == 1
    def sampleLib = project.projectComponents.sampleLib

    assert sampleLib instanceof SampleLibrary
    assert sampleLib.projectPath == project.path
    assert sampleLib.displayName == "DefaultSampleLibrary 'sampleLib'"
}
"""

        then:
        succeeds "checkModel"
    }
}
