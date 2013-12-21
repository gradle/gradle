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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

class IvyBrokenDescriptorIntegrationTest extends AbstractDependencyResolutionTest {
    def "reports Ivy descriptor that cannot be parsed"() {
        server.start()
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
task showBroken << { println configurations.compile.files }
"""

        and:
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').publish()
        module.ivyFile.text = "<ivy-module>"

        when:
        module.ivy.expectGet()

        then:
        fails "showBroken"
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("invalid version null")
    }

    def "reports missing parent descriptor"() {
        server.start()
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
task showBroken << { println configurations.compile.files }
"""

        and:
        def parent = ivyHttpRepo.module('group', 'parent', 'a')
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').extendsFrom(organisation: 'group', module: 'parent', revision: 'a').publish()

        when:
        module.ivy.expectGet()
        parent.ivy.expectGetMissing()
        parent.jar.expectHeadMissing()

        then:
        fails "showBroken"
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("Could not find any version that matches group:parent:a.")
    }

    def "reports parent descriptor that cannot be parsed"() {
        server.start()
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
task showBroken << { println configurations.compile.files }
"""

        and:
        def parent = ivyHttpRepo.module('group', 'parent', 'a').publish()
        parent.ivyFile.text = '<ivy-module/>'
        def module = ivyHttpRepo.module('group', 'projectA', '1.2').extendsFrom(organisation: 'group', module: 'parent', revision: 'a').publish()

        when:
        module.ivy.expectGet()
        parent.ivy.expectGet()

        then:
        fails "showBroken"
        failure
            .assertResolutionFailure(":compile")
            .assertHasCause("Could not parse Ivy file ${module.ivy.uri}")
            .assertHasCause("Could not parse Ivy file ${parent.ivy.uri}")
            .assertHasCause("invalid version null")
    }
}
