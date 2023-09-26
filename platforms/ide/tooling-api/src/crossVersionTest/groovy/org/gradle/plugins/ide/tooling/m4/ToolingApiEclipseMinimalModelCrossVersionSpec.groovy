/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.tooling.m4

import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.WithOldConfigurationsSupport
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject

class ToolingApiEclipseMinimalModelCrossVersionSpec extends ToolingApiSpecification implements WithOldConfigurationsSupport {
    def "minimal Eclipse model does not attempt to resolve external dependencies"() {
        createDirs("child")
        file('settings.gradle').text = 'include "child"'
        file('build.gradle').text = """
apply plugin: 'java'
dependencies {
    ${implementationConfiguration} project(':child')
    ${implementationConfiguration} files { throw new RuntimeException() }
    ${implementationConfiguration} 'this.lib.surely.does.not.exist:indeed:1.0'
}
project(':child') {
    apply plugin: 'java'
    dependencies {
        ${implementationConfiguration} files { throw new RuntimeException() }
        ${implementationConfiguration} 'this.lib.surely.does.not.exist:indeed:2.0'
    }
}
"""

        when:
        HierarchicalEclipseProject project = loadToolingModel(HierarchicalEclipseProject)

        then:
        project.projectDependencies.size() == 1
        project.projectDependencies[0].path == 'child'
    }

    def "minimal Eclipse model does not attempt to call any tasks"() {
        file('build.gradle').text = '''
apply plugin: 'java'

sourceSets.main.output.dir "$buildDir/foo", builtBy: 'generateResources'

task generateResources {
    doLast {
        assert false : 'should not be called when building minimal model'
    }
}
'''

        when:
        loadToolingModel(HierarchicalEclipseProject)

        then:
        noExceptionThrown()
    }

}
