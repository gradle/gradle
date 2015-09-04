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
import spock.lang.Unroll

class IvyDescriptorResolveIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def "substitutes system properties into ivy descriptor"() {
        given:
        ivyRepo.module("org.gradle", "test", "1.45")
                .dependsOn('org.gradle.${sys_prop}', 'module_${sys_prop}', 'v_${sys_prop}')
                .publish()

        ivyRepo.module("org.gradle.111", "module_111", "v_111").publish()

        and:
        buildFile << """
repositories { ivy { url "${ivyRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check << {
    configurations.compile.resolvedConfiguration.firstLevelModuleDependencies.each {
        it.children.each { transitive ->
            assert transitive.moduleGroup == "org.gradle.111"
            assert transitive.moduleName == "module_111"
            assert transitive.moduleVersion == "v_111"
        }
    }
    assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'module_111-v_111.jar']
}
"""

        when:
        executer.withArgument("-Dsys_prop=111")

        then:
        succeeds "check"
    }

    def "merges values from parent descriptor file that is available locally"() {
        given:
        def parentModule = ivyHttpRepo.module("org.gradle.parent", "parent_module", "1.1").dependsOn("org.gradle.dep", "dep_module", "1.1").publish()
        def depModule = ivyHttpRepo.module("org.gradle.dep", "dep_module", "1.1").publish()

        def module = ivyHttpRepo.module("org.gradle", "test", "1.45")
        module.extendsFrom(organisation: "org.gradle.parent", module: "parent_module", revision: "1.1", location: parentModule.ivyFile.toURI().toURL())
        parentModule.publish()
        module.publish()

        when:
        buildFile << """
repositories { ivy { url "${ivyHttpRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'dep_module-1.1.jar']
}
"""

        and:
        module.ivy.expectGet()
        depModule.ivy.expectGet()
        module.jar.expectGet()
        depModule.jar.expectGet()

        then:
        succeeds "check"

        when:
        server.resetExpectations()

        then:
        succeeds "check"
    }

    def "merges values from parent descriptor file"() {
        given:
        final parentModule = ivyHttpRepo.module("org.gradle.parent", "parent_module", "1.1").dependsOn("org.gradle.dep", "dep_module", "1.1").publish()
        final depModule = ivyHttpRepo.module("org.gradle.dep", "dep_module", "1.1").publish()

        final module = ivyHttpRepo.module("org.gradle", "test", "1.45")
        final extendAttributes = [organisation: "org.gradle.parent", module: "parent_module", revision: "1.1"]
        module.extendsFrom(extendAttributes)
        parentModule.publish()
        module.publish()

        when:
        buildFile << """
repositories { ivy { url "${ivyHttpRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle:test:1.45"
}

task check << {
    assert configurations.compile.collect { it.name } == ['test-1.45.jar', 'dep_module-1.1.jar']
}
"""

        and:
        module.ivy.expectGet()
        parentModule.ivy.expectGet()
        depModule.ivy.expectGet()
        module.jar.expectGet()
        depModule.jar.expectGet()

        then:
        succeeds "check"

        when:
        server.resetExpectations()

        then:
        succeeds "check"
    }

    @Unroll
    def "excludes transitive dependencies when ivy.xml has dependency declared with #name"() {
        given:

        ivyRepo.module("org.gradle.dep", "dep_module", "1.134")
                .dependsOn("org.gradle.one", "mod_one", "1.1")
                .dependsOn("org.gradle.two", "mod_one", "2.1")
                .dependsOn("org.gradle.two", "mod_two", "2.2")
                .publish()
        ivyRepo.module("org.gradle.one", "mod_one", "1.1").artifact([:]).artifact([type: 'war']).publish()
        ivyRepo.module("org.gradle.two", "mod_one", "2.1").publish()
        ivyRepo.module("org.gradle.two", "mod_two", "2.2").publish()

        ivyRepo.module("org.gradle.test", "test_exclude", "1.134")
                .dependsOn("org.gradle.dep", "dep_module", "1.134")
                .withXml({
            asNode().dependencies[0].dependency[0].appendNode("exclude", excludeAttributes)
        })
                .publish()

        and:
        buildFile << """
repositories { ivy { url "${ivyRepo.uri}" } }
configurations { compile }
dependencies {
    compile "org.gradle.test:test_exclude:1.134"
}

task check(type: Sync) {
    into "libs"
    from configurations.compile
}
"""

        when:
        succeeds "check"

        then:
        def jars = ['test_exclude-1.134.jar'] + transitiveJars
        file("libs").assertHasDescendants(jars.toArray(new String[0]))

        where:
        name                       | excludeAttributes                          | transitiveJars
        "empty exclude"            | [:]                                        | ['dep_module-1.134.jar'] // Does not exclude the depended-on module itself
        "unmatched exclude"        | [module: "different"]                      | ['dep_module-1.134.jar', 'mod_one-1.1.jar', 'mod_one-1.1.war', 'mod_one-2.1.jar', 'mod_two-2.2.jar']
        "module exclude"           | [module: "mod_one"]                        | ['dep_module-1.134.jar', 'mod_two-2.2.jar']
        "org exclude"              | [org: "org.gradle.two"]                    | ['dep_module-1.134.jar', 'mod_one-1.1.jar', 'mod_one-1.1.war']
        "module and org exclude"   | [org: "org.gradle.two", module: "mod_one"] | ['dep_module-1.134.jar', 'mod_one-1.1.jar', 'mod_one-1.1.war', 'mod_two-2.2.jar']
        "regex module exclude"     | [module: "mod.*"]                          | ['dep_module-1.134.jar']
        "matching config exclude"  | [module: "mod_one", conf: "default,other"] | ['dep_module-1.134.jar', 'mod_two-2.2.jar']
        "unmatched config exclude" | [module: "mod_one", conf: "other"]         | ['dep_module-1.134.jar', 'mod_one-1.1.jar', 'mod_one-1.1.war', 'mod_one-2.1.jar', 'mod_two-2.2.jar']
        "type exclude"             | [type: "war"]                              | ['dep_module-1.134.jar', 'mod_one-1.1.jar', 'mod_one-2.1.jar', 'mod_two-2.2.jar']
        "extension exclude"        | [ext: "jar"]                               | ['mod_one-1.1.war']
        "name exclude"             | [name: "dep_module-*"]                     | ['mod_one-1.1.jar', 'mod_one-1.1.war', 'mod_one-2.1.jar', 'mod_two-2.2.jar']
    }

    def "transitive dependencies are only excluded if excluded from each dependency declaration"() {
//        c -> d,e
//        a -> c (excludes 'd')
//        b -> c (excludes 'd', 'e')
        given:
        ivyRepo.module("d").publish()
        ivyRepo.module("e").publish()
        ivyRepo.module("c").dependsOn("d").dependsOn("e").publish()

        ivyRepo.module("a")
                .dependsOn("c")
                .withXml({
            asNode().dependencies[0].dependency[0].appendNode("exclude", [module: "d"])
        })
                .publish()
        ivyRepo.module("b")
                .dependsOn("c")
                .withXml({
            def dep = asNode().dependencies[0].dependency[0]
            dep.appendNode("exclude", [module: "d"])
            dep.appendNode("exclude", [module: "e"])
        })
                .publish()

        and:
        buildFile << """
repositories { ivy { url "${ivyRepo.uri}" } }
configurations {
    merged
}
dependencies {
    merged "org.gradle.test:a:1.0", "org.gradle.test:b:1.0"
}

task syncMerged(type: Sync) {
    from configurations.merged
    into "libs"
}
"""

        when:
        succeeds "syncMerged"

        then:
        file("libs").assertHasDescendants(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar'] as String[])
    }
}
