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

package org.gradle.plugins.ide.tooling.r30

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.tooling.model.eclipse.EclipseProject

@ToolingApiVersion('>=3.0')
@TargetGradleVersion(">=3.0")
class ToolingApiEclipseModelCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {

    String localMaven

    def setup() {
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module("org.example", "example-lib", "1.0").publish()
        localMaven = "maven { url '${mavenRepo.uri}' }"
    }

    // TODO (donat) add more coverage after all classpath entry types are exposed via the TAPI

    def "respects manipulation done in the eclipse.classpath.whenMerged closure"() {
        setup:
        settingsFile << 'rootProject.name = "root"'
        buildFile <<
        """apply plugin: 'java'
           apply plugin: 'eclipse'

           repositories { $localMaven }
           dependencies { ${implementationConfiguration} 'org.example:example-lib:1.0' }

           eclipse {
               classpath {
                   file {
                       whenMerged { classpath ->
                           classpath.entries.find { it.kind == "lib" }.entryAttributes['customkey'] = 'whenMerged'
                       }
                   }
               }
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)
        def attributes = project.classpath[0].classpathAttributes

        then:
        attributes.find { it.name == 'customkey' && it.value == 'whenMerged'}
    }
}
