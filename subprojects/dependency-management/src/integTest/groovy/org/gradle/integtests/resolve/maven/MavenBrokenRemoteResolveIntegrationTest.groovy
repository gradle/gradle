/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.resolve.maven

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Unroll

class MavenBrokenRemoteResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    @ToBeFixedForInstantExecution
    public void "reports and recovers from missing module"() {
        given:
        def repo = mavenHttpRepo("repo1")
        def module = repo.module("group", "projectA", "1.2").publish()

        buildFile << """
repositories {
    maven { url "${repo.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        when:
        module.pom.expectGetMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertResolutionFailure(':missing')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${module.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
    project :""")

        when:
        module.pom.expectGetMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
            .assertResolutionFailure(':missing')
            .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${module.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
    project :""")

        when:
        server.resetExpectations()
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForInstantExecution
    public void "reports and recovers from multiple missing modules"() {
        given:
        def repo = mavenHttpRepo("repo1")
        def moduleA = repo.module("group", "projectA", "1.2").publish()
        def moduleB = repo.module("group", "projectB", "1.0-milestone-9").publish()

        buildFile << """
repositories {
    maven { url "${repo.uri}"}
}
configurations { missing }
dependencies {
    missing 'group:projectA:1.2'
    missing 'group:projectB:1.0-milestone-9'
}
task showMissing { doLast { println configurations.missing.files } }
"""

        when:
        moduleA.pom.expectGetMissing()
        moduleB.pom.expectGetMissing()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
                .assertResolutionFailure(':missing')
                .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${moduleA.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
    project :""")
                .assertHasCause("""Could not find group:projectB:1.0-milestone-9.
Searched in the following locations:
  - ${moduleB.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
    project :""")

        when:
        server.resetExpectations()
        moduleA.pom.expectGet()
        moduleA.artifact.expectGet()
        moduleB.pom.expectGet()
        moduleB.artifact.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForInstantExecution
    public void "reports and recovers from multiple missing transitive modules"() {
        settingsFile << "include 'child1'"

        given:
        def repo = mavenHttpRepo("repo1")
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
        maven { url "${repo.uri}"}
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
        moduleA.pom.expectGetMissing()
        moduleB.pom.expectGetMissing()
        moduleC.pom.expectGet()
        moduleD.pom.expectGet()

        then:
        fails("showMissing")
        failure.assertHasDescription('Execution failed for task \':showMissing\'.')
                .assertResolutionFailure(':compile')
                .assertHasCause("""Could not find group:projectA:1.2.
Searched in the following locations:
  - ${moduleA.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
    project : > group:projectC:0.99
    project : > project :child1 > group:projectD:1.0GA""")
                .assertHasCause("""Could not find group:projectB:1.0-milestone-9.
Searched in the following locations:
  - ${moduleB.pom.uri}
If the artifact you are trying to retrieve can be found in the repository but without metadata in 'Maven POM' format, you need to adjust the 'metadataSources { ... }' of the repository declaration.
Required by:
    project : > project :child1 > group:projectD:1.0GA""")

        when:
        server.resetExpectations()
        moduleA.pom.expectGet()
        moduleA.artifact.expectGet()
        moduleB.pom.expectGet()
        moduleB.artifact.expectGet()
        moduleC.artifact.expectGet()
        moduleD.artifact.expectGet()

        then:
        succeeds('showMissing')

        when:
        server.resetExpectations()

        then:
        succeeds('showMissing')
    }

    @ToBeFixedForInstantExecution
    void "reports and recovers from failed POM download"() {
        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    maven {
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
        module.pom.expectGetBroken()
        fails("showBroken")

        then:
        failure
            .assertHasDescription('Execution failed for task \':showBroken\'.')
            .assertResolutionFailure(':broken')
            .assertHasCause('Could not resolve group:projectA:1.3.')
            .assertHasCause("Could not GET '${module.pom.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds("showBroken")

        when:
        server.resetExpectations()

        then:
        succeeds("showBroken")
    }

    @Unroll("recovers from initial failed POM download (max retries = #retries)")
    void "recovers from initial failed POM download"() {
        withMaxHttpRetryCount(retries)

        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    maven {
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
        (retries-1).times {
            module.pom.expectGetBroken()
        }
        module.pom.expectGet()
        module.artifact.expectGet()

        then:
        succeeds("showBroken")

        where:
        retries << (1..3)
    }

    @Unroll("recovers from initial failed artifact download (max retries = #retries)")
    void "recovers from initial failed artifact download"() {
        withMaxHttpRetryCount(retries)

        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    maven {
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
        module.pom.expectGet()
        (retries-1).times {
            module.artifact.expectGetBroken()
        }
        module.artifact.expectGet()

        then:
        succeeds("showBroken")

        where:
        retries << (1..3)
    }

    @Unroll("doesn't attempt to retry downloading missing POM file (max retries = #retries)")
    void "doesn't attempt to retry downloading missing POM file"() {
        withMaxHttpRetryCount(retries)

        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    maven {
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
        module.pom.expectGetMissing()

        then:
        fails("showBroken")

        and:
        failure
            .assertHasDescription('Execution failed for task \':showBroken\'.')
            .assertResolutionFailure(':broken')
            .assertHasCause('Could not find group:projectA:1.3.')

        where:
        retries << (1..3)
    }

    @Unroll("doesn't attempt to retry downloading missing artifact file (max retries = #retries)")
    void "doesn't attempt to retry downloading missing artifact file"() {
        withMaxHttpRetryCount(retries)

        given:
        def module = mavenHttpRepo.module('group', 'projectA', '1.3').publish()

        buildFile << """
repositories {
    maven {
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
        module.pom.expectGet()
        module.artifact.expectGetMissing()

        then:
        fails("showBroken")

        and:
        failure
            .assertHasDescription('Execution failed for task \':showBroken\'.')
            .assertResolutionFailure(':broken')
            .assertHasCause("Could not resolve all files for configuration ':broken'.")
            .assertHasCause('Could not find projectA-1.3.jar (group:projectA:1.3).')

        where:
        retries << (1..3)
    }

    public void "reports and recovers from failed artifact download"() {
        given:
        buildFile << """
repositories {
    maven {
        url "${mavenHttpRepo.uri}"
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
        def module = mavenHttpRepo.module('group', 'projectA', '1.2').publish()

        when:
        module.pom.expectGet()
        module.artifact.expectGetBroken()

        then:
        fails "retrieve"
        failure.assertHasCause("Could not download projectA-1.2.jar (group:projectA:1.2)")
        failure.assertHasCause("Could not GET '${module.artifact.uri}'. Received status code 500 from server: broken")

        when:
        server.resetExpectations()
        module.artifact.expectGet()

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
