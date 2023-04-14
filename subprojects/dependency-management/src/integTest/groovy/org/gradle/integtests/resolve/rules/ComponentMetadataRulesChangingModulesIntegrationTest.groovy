/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.resolve.rules

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.test.fixtures.HttpRepository

abstract class ComponentMetadataRulesChangingModulesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    abstract HttpRepository getRepo()
    abstract String getRepoDeclaration()

    def moduleA = getRepo().module('org.test', 'moduleA', '1.0')

    def setup() {
        moduleA.publish()
        moduleA.allowAll()
    }

    def "changing dependency doesn't affect changing flag"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules
}

class VerifyingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        println "changing=" + context.details.changing
    }
}

dependencies {
    modules("org.test:moduleA:1.0") { changing = true }
    components {
        all(VerifyingRule)
    }
}
task resolve {
    def files = configurations.modules
    doLast {
        files*.name
    }
}
"""

        expect:
        succeeds("resolve")
        output.count("changing=") == 1
        outputContains("changing=false")
    }

    def "static and dynamic dependencies have changing flag initialized to false"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules
}

class VerifyingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        println "changing=" + context.details.changing
    }
}

dependencies {
    modules "org.test:moduleA:$version"
    components {
        all(VerifyingRule)
    }
}
task resolve {
    def files = configurations.modules
    doLast {
        files*.name
    }
}
"""

        expect:
        succeeds("resolve")
        output.count("changing=") == 1
        outputContains("changing=false")

        where:
        version << ["1.0", "[1.0,2.0]"]
    }

    def "rule can make a component changing"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules {
        resolutionStrategy.cacheChangingModulesFor 0, "seconds"
    }
}

class ChangingTrueRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        context.details.changing = true
    }
}

dependencies {
    modules("org.test:moduleA:1.0")
    components {
        all(ChangingTrueRule)
    }
}
task retrieve(type: Sync) {
    from configurations.modules
    into 'modules'
}
"""

        when:
        run("retrieve")
        def artifact = file("modules/moduleA-1.0.jar")
        def snapshot = artifact.snapshot()

        and:
        moduleA.publishWithChangedContent()
        run("retrieve")

        then:
        artifact.assertContentsHaveChangedSince(snapshot)

        when:
        buildFile << """
class ChangingFalseRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        context.details.changing = false
    }
}

dependencies.components.all(ChangingFalseRule)
"""
        snapshot = artifact.snapshot()
        server.resetExpectations()

        and:
        run("retrieve")

        then:
        artifact.assertContentsHaveNotChangedSince(snapshot)
    }

    def "rule cannot make a dependency non-changing"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules {
        resolutionStrategy.cacheChangingModulesFor 0, "seconds"
    }
}

class UpdatingRule implements ComponentMetadataRule {
    public void execute(ComponentMetadataContext context) {
        context.details.changing = false
    }
}

dependencies {
    modules("org.test:moduleA:1.0") { changing = true }
    components {
        all(UpdatingRule)
    }
}
task retrieve(type: Sync) {
    from configurations.modules
    into 'modules'
}
"""

        when:
        run("retrieve")
        def artifact = file("modules/moduleA-1.0.jar")
        def snapshot = artifact.snapshot()

        and:
        moduleA.publishWithChangedContent()
        run("retrieve")

        then:
        artifact.assertContentsHaveChangedSince(snapshot)
    }
}
