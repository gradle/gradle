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
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

import static org.gradle.integtests.fixtures.SuggestionsMessages.GET_HELP
import static org.gradle.integtests.fixtures.SuggestionsMessages.INFO_DEBUG
import static org.gradle.integtests.fixtures.SuggestionsMessages.SCAN
import static org.gradle.integtests.fixtures.SuggestionsMessages.STACKTRACE_MESSAGE
import static org.gradle.integtests.fixtures.SuggestionsMessages.repositoryHint

class IvyBrokenRemoteResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    public final static String REPOSITORY_HINT = repositoryHint("ivy.xml")

    @ToBeFixedForConfigurationCache
    void "reports and recovers from missing module"() {
        given:
        def repo = ivyHttpRepo("repo1")
        def module = repo.module("group", "projectA", "1.2").publish()

        buildFile << """
repositories {
    ivy { url "${repo.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        when:
        module.ivy.expectGetMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertResolutionFailure(':missing')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${module.ivy.uri}
Required by:
    project :""")

        when:
        module.ivy.expectGetMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertResolutionFailure(':missing')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${module.ivy.uri}
Required by:
    project :""")
        failure.assertHasResolutions(REPOSITORY_HINT,
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)


        when:
        server.resetExpectations()
        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForConfigurationCache
    void "reports and recovers from multiple missing modules"() {
        given:
        def repo = ivyHttpRepo("repo1")
        def moduleA = repo.module("group", "projectA", "1.2").publish()
        def moduleB = repo.module("group", "projectB", "1.0-milestone-9").publish()

        buildFile << """
repositories {
    ivy { url "${repo.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
    missing 'group:projectB:1.0-milestone-9'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        when:
        moduleA.ivy.expectGetMissing()
        moduleB.ivy.expectGetMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertResolutionFailure(':missing')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${moduleA.ivy.uri}
Required by:
    project :""")
            .assertHasCause("""Could not find group:projectB:1.0-milestone-9.
Searched in the following locations:
  - ${moduleB.ivy.uri}
Required by:
    project :""")
        failure.assertHasResolutions(REPOSITORY_HINT,
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)

        when:
        server.resetExpectations()
        moduleA.ivy.expectGet()
        moduleA.jar.expectGet()
        moduleB.ivy.expectGet()
        moduleB.jar.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForConfigurationCache
    void "reports and recovers from multiple missing transitive modules"() {
        settingsFile << "include 'child1'"

        given:
        def repo = ivyHttpRepo("repo1")
        def moduleA = repo.module("group", "projectA", "1.2").publish()
        def moduleB = repo.module("group", "projectB", "1.0-milestone-9").publish()
        def moduleC = repo.module("group", "projectC", "0.99")
            .dependsOn(moduleA)
            .publish()
        def moduleD = repo.module("group", "projectD", "1.0GA")
            .dependsOn(moduleA)
            .dependsOn(moduleB)
            .publish()

        buildFile << """
allprojects {
    repositories {
        ivy { url "${repo.uri}"}
    }
    configurations {
        compile
        'default' {
            extendsFrom(compile)
        }
    }
}
dependencies {
    compile 'group:projectC:0.99'
    compile project(':child1')
}
project(':child1') {
    dependencies {
        compile 'group:projectD:1.0GA'
    }
}
task showMissing { doLast { println configurations.compile.files } }
"""

        when:
        moduleA.ivy.expectGetMissing()
        moduleB.ivy.expectGetMissing()
        moduleC.ivy.expectGet()
        moduleD.ivy.expectGet()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertResolutionFailure(':compile')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${moduleA.ivy.uri}
Required by:
    project : > group:projectC:0.99
    project : > project :child1 > group:projectD:1.0GA""")
            .assertHasCause("""Could not find group:projectB:1.0-milestone-9.
Searched in the following locations:
  - ${moduleB.ivy.uri}
Required by:
    project : > project :child1 > group:projectD:1.0GA""")
        failure.assertHasResolutions(REPOSITORY_HINT,
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)

        when:
        server.resetExpectations()
        moduleA.ivy.expectGet()
        moduleA.jar.expectGet()
        moduleB.ivy.expectGet()
        moduleB.jar.expectGet()
        moduleC.jar.expectGet()
        moduleD.jar.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForConfigurationCache
    void "reports and recovers from missing module when dependency declaration references an artifact"() {
        given:
        def repo = ivyHttpRepo("repo1")
        def module = repo.module("group", "projectA", "1.2").artifact(classifier: 'thing').publish()

        buildFile << """
repositories {
    ivy { url "${repo.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2:thing'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        when:
        module.ivy.expectGetMissing()
        def artifact = module.getArtifact(classifier: 'thing')

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertHasCause('Could not resolve all files for configuration \':missing\'.')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${module.ivy.uri}
Required by:
""")
        failure.assertHasResolutions(REPOSITORY_HINT,
            STACKTRACE_MESSAGE,
            INFO_DEBUG,
            SCAN,
            GET_HELP)

        when:
        server.resetExpectations()
        module.ivy.expectGet()
        artifact.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForConfigurationCache
    void "reports and recovers from module missing from multiple repositories"() {
        given:
        def repo1 = ivyHttpRepo("repo1")
        def repo2 = ivyHttpRepo("repo2")
        def moduleInRepo1 = repo1.module("group", "projectA", "1.2").publish()
        def moduleInRepo2 = repo2.module("group", "projectA", "1.2")

        buildFile << """
repositories {
    ivy { url "${repo1.uri}"}
    ivy { url "${repo2.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        when:
        moduleInRepo1.ivy.expectGetMissing()
        moduleInRepo2.ivy.expectGetMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertHasCause('Could not resolve all files for configuration \':missing\'.')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${moduleInRepo1.ivy.uri}
  - ${moduleInRepo2.ivy.uri}
Required by:
""")

        when:
        server.resetExpectations()
        moduleInRepo1.ivy.expectGet()
        moduleInRepo1.jar.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForConfigurationCache
    void "reports and recovers from missing module when no repositories defined"() {
        given:
        buildFile << """
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        expect:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertResolutionFailure(':missing')
            .assertHasCause("Cannot resolve external dependency group:projectA:1.2 because no repositories are defined.")

        when:
        def module = ivyHttpRepo.module("group", "projectA", "1.2").publish()

        and:
        buildFile << "repositories { ivy { url '${ivyHttpRepo.uri}' } }"

        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForConfigurationCache
    void "reports and recovers from failed Ivy descriptor download"() {
        given:
        def module = ivyHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { broken }
dependencies {
    broken 'group:projectA:1.3'
}
task showBroken { doLast { println configurations.broken.files } }
"""

        when:
        module.ivy.expectGetBroken()
        fails("showBroken")

        then:
        failure
            .assertHasDescription('Execution failed for task \':showBroken\'.')
            .assertResolutionFailure(':broken')
            .assertHasCause('Could not resolve group:projectA:1.3.')
            .assertHasCause("Could not GET '${module.ivy.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.ivy.expectGet()
        module.jar.expectGet()

        then:
        succeeds("showBroken")

        when:
        server.resetExpectations()

        then:
        succeeds("showBroken")
    }

    void "reports and caches missing artifact"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.ivy.expectGet()
        module.jar.expectGetMissing()

        then:
        fails "retrieve"

        failure.assertHasCause("""Could not find projectA-1.2.jar (group:projectA:1.2).
Searched in the following locations:
    ${module.jar.uri}""")

        when:
        server.resetExpectations()

        then:
        fails "retrieve"

        failure.assertHasCause("""Could not find projectA-1.2.jar (group:projectA:1.2).
Searched in the following locations:
    ${module.jar.uri}""")
    }

    void "reports and recovers from failed artifact download"() {
        given:
        buildFile << """
repositories {
    ivy {
        url "${ivyHttpRepo.uri}"
    }
}
configurations { compile }
dependencies {
    compile 'group:projectA:1.2'
}
task retrieve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.ivy.expectGet()
        module.jar.expectGetBroken()

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download projectA-1.2.jar (group:projectA:1.2)")
        failure.assertHasCause("Could not GET '${module.jar.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.jar.expectGet()

        then:
        succeeds "retrieve"
        file('libs').assertHasDescendants('projectA-1.2.jar')

        when:
        server.resetExpectations()

        then:
        succeeds "retrieve"
        file('libs').assertHasDescendants('projectA-1.2.jar')
    }
}
