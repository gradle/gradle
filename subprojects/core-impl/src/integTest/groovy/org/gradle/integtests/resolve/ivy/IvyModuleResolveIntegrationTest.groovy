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
import spock.lang.Unroll

class IvyModuleResolveIntegrationTest extends AbstractDependencyResolutionTest {
    def "correctly handles wildcard on LHS of configuration mapping"() {
        given:
        buildFile << """
configurations {
    compile
}
dependencies {
    repositories {
        ivy { url "${ivyRepo.uri}" }
    }
    compile 'ivy.configuration:projectA:1.2'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""
        when: "projectA uses a wildcard configuration mapping for dependency on projectB"
        def moduleA = ivyRepo.module('ivy.configuration', 'projectA', '1.2')
                .configuration('parent')
                .artifact()
                .dependsOn(organisation: 'ivy.configuration', module: 'projectB', revision: '1.5', conf: 'runtime->*')
                .publish()

        ivyRepo.module('ivy.configuration', 'projectB', '1.5')
                .configuration('child')
                .artifact()
                .artifact([name: 'projectB', conf: 'runtime'])
                .artifact([name: 'projectB-child', conf: 'child'])
                .dependsOn(organisation: 'ivy.configuration', module: 'projectC', revision: '1.7', conf: 'child->*')
                .publish()

        ivyRepo.module('ivy.configuration', 'projectC', '1.7')
                .artifact()
                .publish()

        and:
        succeeds 'retrieve'

        then: "artifacts and dependencies from all configurations of projectB are included"
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar', 'projectB-child-1.5.jar', 'projectC-1.7.jar')

        when: "projectB-1.5 is replaced by conflict resolution with projectB-1.6 that has a different set of configurations"

        ivyRepo.module('ivy.configuration', 'projectB', '1.6')
                .configuration('other')
                .artifact([name: 'projectB-other', conf: 'other'])
                .publish()

        ivyRepo.module('ivy.configuration', 'projectD', '1.0')
                .dependsOn('ivy.configuration', 'projectB', '1.6')
                .publish()

        moduleA.dependsOn('ivy.configuration', 'projectD', '1.0').publish()

        and:
        succeeds 'retrieve'

        then: "we resolve artifacts from projectB-1.6 only"
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-other-1.6.jar', 'projectD-1.0.jar')
    }

    @Unroll
    def "correctly handles configuration mapping rule '#rule'"() {
        given:
        buildFile << """
configurations {
    compile
}
dependencies {
    repositories {
        ivy { url "${ivyRepo.uri}" }
    }
    compile group: 'ivy.configuration', name: 'projectA', version: '1.2', configuration: 'a'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""
        ivyRepo.module('ivy.configuration', 'projectA', '1.2')
                .configuration("parent")
                .configuration("a", ["parent"])
                .configuration("b")
                .dependsOn(organisation: 'ivy.configuration', module: 'projectB', revision: '1.5', conf: rule)
                .publish()

        ivyRepo.module('ivy.configuration', 'projectB', '1.5')
                .configuration('a')
                .configuration('b')
                .configuration('other')
                .artifact([name: 'projectB-a', conf: 'a'])
                .artifact([name: 'projectB-b', conf: 'b'])
                .dependsOn(organisation: 'ivy.configuration', module: 'projectC', revision: '1.7', conf: 'a->default')
                .dependsOn(organisation: 'ivy.configuration', module: 'projectD', revision: '1.7', conf: 'b->default')
                .publish()

        ivyRepo.module('ivy.configuration', 'projectC', '1.7').publish()
        ivyRepo.module('ivy.configuration', 'projectD', '1.7').publish()

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants(* (['projectA-1.2.jar'] + jars))

        where:
        rule                    | jars
        "a"                     | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->b"                  | ["projectB-b-1.5.jar", "projectD-1.7.jar"]
        "a,b->b"                | ["projectB-b-1.5.jar", "projectD-1.7.jar"]
        "parent->a"             | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a,parent->a"           | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->a,b"                | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
        "a;a->b"                | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
        "*->a"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "*->*"                  | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
        "*->@"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a,b->@"                | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "runtime->unknown;%->@" | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->a;%->b"             | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
//        "*,!b->b"               | ["projectB-b-1.5.jar", "projectD-1.7.jar"]
        "b"                     | []
//        "*,!a->a"               | []
        "a->#"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "parent->#"             | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "*->#"                  | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "*->unknown(a)"         | ["projectB-a-1.5.jar", "projectC-1.7.jar"]
        "a->unknown(*)"         | ["projectB-a-1.5.jar", "projectB-b-1.5.jar", "projectC-1.7.jar", "projectD-1.7.jar"]
    }

    def "prefers revConstraint over rev when dynamic resolve mode is used"() {
        given:
        buildFile << """
configurations {
    compile
}
dependencies {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            resolve.dynamicMode = project.hasProperty('useDynamicResolve')
        }
    }
    compile 'org:projectA:1.2'
}
task retrieve(type: Sync) {
  from configurations.compile
  into 'libs'
}
"""
        ivyRepo.module('org', 'projectA', '1.2')
                .dependsOn(organisation: 'org', module: 'projectB', revision: '1.5', revConstraint: '1.6')
                .dependsOn(organisation: 'org', module: 'projectC', revision: 'alpha-12')
                .publish()

        ivyRepo.module('org', 'projectB', '1.5')
                .publish()

        ivyRepo.module('org', 'projectB', '1.6')
                .publish()

        ivyRepo.module('org', 'projectC', 'alpha-12')
                .publish()

        when:
        executer.withArguments("-PuseDynamicResolve=true")
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.6.jar', 'projectC-alpha-12.jar')

        when:
        run 'retrieve'

        then:
        file('libs').assertHasDescendants('projectA-1.2.jar', 'projectB-1.5.jar', 'projectC-alpha-12.jar')
    }
}
