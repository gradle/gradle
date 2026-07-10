/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.plugins.ide.tooling.r74

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

@TargetGradleVersion('>=7.4')
class CompositeBuildSubstitutionsCrossVersionSpec extends ToolingApiSpecification {

    private compositeIncludedRoot() {
        settingsFile << """
            rootProject.name = 'module-root'
            includeBuild('.')
            include('sub-a', 'sub-b')
        """
        file('sub-a').mkdir()
        file('sub-a/build.gradle') << """
            plugins { id 'java-library' }
            group = 'g'
        """
        file('sub-b/build.gradle') << """
            plugins { id 'java-library' }
            group = 'g'
            dependencies {
                implementation 'g:sub-a'
            }
        """
    }

    def "Eclipse model respects composite build substitutions in root build"() {
        given:
        compositeIncludedRoot()

        when:
        def model = withConnection {
            getModel(HierarchicalEclipseProject)
        }

        then:
        model.children[1].projectDependencies.size() == 1
        model.children[1].projectDependencies[0].path == 'sub-a'
    }
}
