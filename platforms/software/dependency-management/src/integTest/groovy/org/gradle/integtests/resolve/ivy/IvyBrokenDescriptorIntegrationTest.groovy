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

package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveFailureTestFixture

class IvyBrokenDescriptorIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveFailureTestFixture failedResolve = new ResolveFailureTestFixture(buildFile)

    def setup() {
        buildFile << """
            repositories {
                ivy {
                    url = "${ivyHttpRepo.uri}"
                }
            }
            configurations { compile }
        """
        failedResolve.prepare("compile")
    }

    def "reports Ivy descriptor that cannot be parsed"() {
        given:
        buildFile << """
dependencies {
    compile 'group:projectA:1.2'
}
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        module.ivyFile.text = "<ivy-module>"

        when:
        module.ivy.expectGet()

        then:
        fails "checkDeps"
        failedResolve.assertFailurePresent(failure)
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("invalid version null")
    }

    def "reports local Ivy descriptor that cannot be parsed"() {
        given:
        buildFile << """
repositories.clear()
repositories {
    ivy {
        url = "${ivyRepo.uri}"
    }
}
dependencies {
    compile 'group:projectA:1.2'
}
"""

        and:
        def module = ivyRepo.module('group', 'projectA', '1.2').publish()
        module.ivyFile.text = "<ivy-module>"

        when:
        fails "checkDeps"

        then:
        failedResolve.assertFailurePresent(failure)
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivyFile}")
            .assertHasCause("invalid version null")
    }

    def "reports Ivy descriptor with configuration that extends unknown configuration"() {
        given:
        buildFile << """
dependencies {
    compile 'group:projectA:1.2'
}
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').configuration('conf', extendsFrom: ['unknown']).publish()

        when:
        module.ivy.expectGet()

        then:
        fails "checkDeps"
        failedResolve.assertFailurePresent(failure)
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("Configuration 'conf' extends configuration 'unknown' which is not declared.")
    }

    def "reports Ivy descriptor with artifact mapped to unknown configuration"() {
        given:
        buildFile << """
dependencies {
    compile 'group:projectA:1.2'
}
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').artifact(conf: 'default,unknown').publish()

        when:
        module.ivy.expectGet()

        then:
        fails "checkDeps"
        failedResolve.assertFailurePresent(failure)
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("Artifact projectA.jar is mapped to configuration 'unknown' which is not declared.")
    }

    def "reports missing parent descriptor"() {
        given:
        buildFile << """
dependencies {
    compile 'group:projectA:1.2'
}
"""

        and:
        def parent = ivyHttpRepo.module('group', 'parent', 'a')
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').extendsFrom(organisation: 'group', module: 'parent', revision: 'a').publish()

        when:
        module.ivy.expectGet()
        parent.ivy.expectGetMissing()

        then:
        fails "checkDeps"
        failedResolve.assertFailurePresent(failure)
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("""Could not find group:parent:a.
Searched in the following locations:
  - ${parent.ivy.uri}""")
    }

    def "reports parent descriptor that cannot be parsed"() {
        given:
        buildFile << """
dependencies {
    compile 'group:projectA:1.2'
}
"""

        and:
        def parent = ivyHttpRepo.module('group', 'parent', 'a').publish()
        parent.ivyFile.text = '<ivy-module/>'
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').extendsFrom(organisation: 'group', module: 'parent', revision: 'a').publish()

        when:
        module.ivy.expectGet()
        parent.ivy.expectGet()

        then:
        fails "checkDeps"
        failedResolve.assertFailurePresent(failure)
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("Could not parse Ivy file ${parent.ivy.uri}")
            .assertHasCause("invalid version null")
    }
}
