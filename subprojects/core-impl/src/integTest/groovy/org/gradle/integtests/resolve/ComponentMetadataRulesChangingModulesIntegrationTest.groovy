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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.test.fixtures.HttpRepository

abstract class ComponentMetadataRulesChangingModulesIntegrationTest extends AbstractDependencyResolutionTest {
    abstract HttpRepository getRepo()
    abstract String getRepoDeclaration()

    def "rule can flag a module as changing"() {
        server.start()
        def moduleA = getRepo().module('org.test', 'projectA', '1.0').publish()
        def moduleB = getRepo().module('org.test', 'projectB', '1.0').publish()
        moduleA.allowAll()
        moduleB.allowAll()

        writeBuildScriptWithChangingModuleNamed("projectA")

        when:
        moduleA.publish()
        moduleB.publish()
        run("resolveA", "resolveB")

        then:
        executedAndNotSkipped(":resolveA", ":resolveB")

        when:
        moduleA.publishWithChangedContent()
        moduleB.publishWithChangedContent()
        run("resolveA", "resolveB")

        then:
        executedAndNotSkipped(":resolveA")
        skipped(":resolveB")

        when:
        writeBuildScriptWithChangingModuleNamed("")
        moduleA.publishWithChangedContent()
        moduleB.publishWithChangedContent()
        run("resolveA", "resolveB")

        then:
        skipped(":resolveA", ":resolveB")
    }

    private writeBuildScriptWithChangingModuleNamed(String changingModuleName) {
        buildFile.text = getRepoDeclaration()
        buildFile <<
"""
configurations {
    configA
    configB
}
configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, "seconds"
}
dependencies {
    configA "org.test:projectA:1.0"
    configB "org.test:projectB:1.0"
    components {
        eachComponent { details ->
            details.changing = details.id.name == "${changingModuleName}"
        }
    }
}
task resolveA(type: Sync) {
    from configurations.configA
    into 'libsA'
}
task resolveB(type: Sync) {
    from configurations.configB
    into 'libsB'
}
"""
    }
}