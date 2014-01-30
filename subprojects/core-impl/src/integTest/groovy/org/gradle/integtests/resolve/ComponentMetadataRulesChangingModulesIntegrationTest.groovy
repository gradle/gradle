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

    def moduleA = getRepo().module('org.test', 'moduleA', '1.0')

    def setup() {
        server.start()
        moduleA.publish()
        moduleA.allowAll()
    }

    def "changing dependencies have changing flag initialized to true"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules
}
dependencies {
    modules("org.test:moduleA:1.0") { changing = true }
    components {
        eachComponent { details ->
            file("changing").text = details.changing
        }
    }
}
task resolve << {
    configurations.modules.resolve()
}
"""

        when:
        run("resolve")

        then:
        file("changing").text == "true"
    }

    def "static and dynamic dependencies have changing flag initialized to false"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules
}
dependencies {
    modules "org.test:moduleA:$version"
    components {
        eachComponent { details ->
            file("changing").text = details.changing
        }
    }
}
task resolve << {
    configurations.modules.resolve()
}
"""

        when:
        run("resolve")

        then:
        file("changing").text == "false"

        where:
        version << ["1.0", "[1.0,2.0]"]
    }


    def "rule can change a non-changing component to changing"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules {
        resolutionStrategy.cacheChangingModulesFor 0, "seconds"
    }
}
dependencies {
    modules("org.test:moduleA:1.0")
    components {
        eachComponent { it.changing = true }
    }
}
task resolve << {
    copy {
        from configurations.modules
        into "modules"
    }
}
"""

        when:
        run("resolve")
        def artifact = file("modules/moduleA-1.0.jar")
        def snapshot = artifact.snapshot()

        and:
        moduleA.publishWithChangedContent()
        run("resolve")

        then:
        artifact.assertContentsHaveChangedSince(snapshot)
    }

    def "rule can change a changing component to non-changing"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules {
        resolutionStrategy.cacheChangingModulesFor 0, "seconds"
    }
}
dependencies {
    modules("org.test:moduleA:1.0") { changing = true }
    components {
        eachComponent { it.changing = false }
    }
}
task resolve << {
    copy {
        from configurations.modules
        into "modules"
    }
}
"""

        when:
        run("resolve")
        def artifact = file("modules/moduleA-1.0.jar")
        def snapshot = artifact.snapshot()

        and:
        moduleA.publishWithChangedContent()
        run("resolve")

        then:
        artifact.assertContentsHaveNotChangedSince(snapshot)
    }

    def "changes made by metadata rule aren't cached"() {
        buildFile <<
"""
$repoDeclaration
configurations {
    modules
}
dependencies {
    modules "org.test:moduleA:1.0"
    components {
        eachComponent {
            file("changing").text = it.changing
            it.changing = true
        }
    }
}
task resolve << {
    configurations.modules.resolve()
}
"""

        when:
        run("resolve")
        run("resolve")

        then:
        file("changing").text == "false"
    }
}