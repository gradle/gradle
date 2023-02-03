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

package org.gradle.integtests.resolve.ivy

import org.gradle.test.fixtures.ivy.IvyModule
import spock.lang.Issue

/**
 * Demonstrates the use of Ivy dependency excludes.
 *
 * @see <a href="http://ant.apache.org/ivy/history/latest-milestone/ivyfile/artifact-exclude.html">Ivy reference documentation</a>
 */
class IvyDescriptorDependencyExcludeResolveIntegrationTest extends AbstractIvyDescriptorExcludeResolveIntegrationTest {
    /**
     * Dependency exclude for a single artifact by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     */
    def "dependency exclude having single artifact with #name"() {
        given:
        ivyRepo.module('b').publish()
        ivyRepo.module('c').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                           | excludeAttributes | resolvedJars
        'non-matching module'          | [module: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'non-matching artifact'        | [name: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'module on other dependency'   | [module: 'c']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'artifact on other dependency' | [name: 'c']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching module'              | [module: 'b']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar'] // Module exclude does not apply to declaring module
        'matching artifact'            | [name: 'b']       | ['a-1.0.jar', 'c-1.0.jar'] // Artifact exclude does apply to declaring module
    }

    /**
     * Exclude of transitive dependency with a single artifact by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> e
     *
     * Exclude is applied to dependency a->b
     */
    def "transitive dependency exclude having single artifact with #name"() {
        given:
        ivyRepo.module('d').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                    | excludeAttributes | resolvedJars
        'non-matching module'   | [module: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
        'non-matching artifact' | [name: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
        'matching all modules'  | [module: '*']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching module'       | [module: 'd']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching artifact'     | [name: 'd']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
    }
    /**
     * Exclude of transitive dependency involved in a dependency cycle.
     *
     * Dependency graph:
     * a -> b -> c -> d -> c
     *
     * 'c' is excluded on dependency a->b
     */
    def "module involved in dependency cycle with excluded #name"() {
        given:
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        moduleA.publish()
        ivyRepo.module('b').dependsOn('c').publish()
        ivyRepo.module('c').dependsOn('d').publish()
        ivyRepo.module('d').dependsOn('c').publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name               | excludeAttributes | resolvedJars
        'same module'      | [module: 'b']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'dependent module' | [module: 'c']     | ['a-1.0.jar', 'b-1.0.jar']
        'artifact'         | [name: 'c']       | ['a-1.0.jar', 'b-1.0.jar', 'd-1.0.jar']
    }

    /**
     * Exclude of transitive dependency with a single artifact does not exclude its transitive module by using a combination of name exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d -> f
     * c -> e
     */
    def "transitive dependency exclude having single artifact with #name does not exclude its transitive module"() {
        given:
        ivyRepo.module('f').publish()
        ivyRepo.module('d').dependsOn('f').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                    | excludeAttributes | resolvedJars
        'non-matching artifact' | [name: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'matching artifact'     | [name: 'd']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
    }

    /**
     * Exclude of transitive dependency with multiple artifacts by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> e
     */
    def "transitive dependency exclude having multiple artifacts with #name"() {
        given:
        ivyRepo.module('d')
            .artifact([:])
            .artifact([type: 'sources', classifier: 'sources', ext: 'jar'])
            .artifact([type: 'javadoc', classifier: 'javadoc', ext: 'jar'])
            .publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                     | excludeAttributes        | resolvedJars
        'non-matching module'    | [module: 'other']        | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'non-matching artifact'  | [name: 'other']          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'matching all modules'   | [module: '*']            | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching module'        | [module: 'd']            | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching artifact'      | [name: 'd']              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching name and type' | [name: 'd', type: 'jar'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
    }

    /**
     * When a module is depended on via multiple paths and excluded on one of those paths, it is not excluded.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    def "when a module is depended on via multiple paths and excluded on only one of those paths, it is not excluded (#name)"() {
        given:
        ivyRepo.module('d').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('c').dependsOn('d').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar'])

        where:
        name                    | excludeAttributes
        'non-matching module'   | [module: 'other']
        'non-matching artifact' | [name: 'other']
        'matching all modules'  | [module: '*']
        'matching module'       | [module: 'd']
        'matching artifact'     | [name: 'd']
    }

    /**
     * When a module artifact is depended on via multiple paths and excluded on one of those paths, it is not excluded.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    def "when a module artifact is depended on via multiple paths and excluded on one of those paths, it is not excluded"() {
        given:
        ivyRepo.module('d').artifact([type: 'war']).artifact([type: 'ear']) publish()
        def moduleB = ivyRepo.module('b').dependsOn('d')
        addExcludeRuleToModuleDependency(moduleB, 'd', [type: 'war'])
        moduleB.publish()
        def moduleC = ivyRepo.module('c').dependsOn('d')
        addExcludeRuleToModuleDependency(moduleC, 'd', [type: 'ear'])
        moduleC.publish()
        ivyRepo.module('a').dependsOn('b').dependsOn('c').publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.ear', 'd-1.0.war'])
    }

    /**
     * When a module is depended on via multiple paths and excluded on all of those paths, it is excluded.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    def "when a module is depended on via multiple paths and excluded on all of those paths, it is excluded (#name)"() {
        given:
        ivyRepo.module('d').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('c').dependsOn('d').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        addExcludeRuleToModuleDependency(moduleA, 'c', excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                    | excludeAttributes | resolvedJars
        'non-matching module'   | [module: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'non-matching artifact' | [name: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching module'       | [module: 'd']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching artifact'     | [name: 'd']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
    }

    /**
     * When a module is depended on via multiple paths, it is excluded only if excluded on each of the paths.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    def "when a module is depended on via multiple paths, it is excluded only if excluded on each of the paths (#name)"() {
        given:
        ivyRepo.module('d').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('c').dependsOn('d').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')

        addExcludeRuleToModuleDependency(moduleA, 'b', excludePath1)
        addExcludeRuleToModuleDependency(moduleA, 'c', excludePath2)

        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                          | excludePath1  | excludePath2             | resolvedJars
        'non-matching group'          | [module: 'd'] | [org: 'org.other']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'non-matching module'         | [module: 'e'] | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'non-matching artifact'       | [name: 'e']   | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching group and module'   | [module: 'd'] | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching group and artifact' | [name: 'd']   | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
    }

    /**
     * When a module is depended on via a single chained path, it is excluded if excluded on any of the links in that path.
     *
     * Dependency graph:
     * a -> b -> c -> d
     */
    def "when a module is depended on via a single chained path, it is excluded if excluded on any of the links in that path (#name)"() {
        given:
        ivyRepo.module('d').publish()
        IvyModule moduleC = ivyRepo.module('c').dependsOn('d')
        IvyModule moduleB = ivyRepo.module('b').dependsOn('c')
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b')

        addExcludeRuleToModuleDependency(moduleA, 'b', excludePath1)
        addExcludeRuleToModuleDependency(moduleB, 'c', excludePath2)

        moduleB.publish()
        moduleC.publish()
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                  | excludePath1  | excludePath2             | resolvedJars
        'excluded by module'  | [module: 'd'] | [module: 'e']            | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'exclude by artifact' | [name: 'd']   | [name: 'e']              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'excluded by group'   | [module: 'e'] | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'not excluded'        | [name: 'e']   | [org: 'org.other']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
    }

    /**
     * Exclude of transitive dependency without provided group or module attribute does not exclude its transitive module by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d -> f
     * c -> e
     */
    @Issue("https://issues.gradle.org/browse/GRADLE-2674")
    def "transitive dependency exclude without provided group or module attribute but matching #name does not exclude its transitive module"() {
        given:
        ivyRepo.module('f')
            .artifact([:])
            .artifact([type: 'war'])
            .publish()
        ivyRepo.module('d')
            .artifact([:])
            .artifact([type: 'war'])
            .artifact([type: 'ear'])
            .dependsOn('f')
            .publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                            | excludeAttributes              | resolvedJars
        "type 'war'"                    | [type: 'war']                  | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'd-1.0.ear', 'e-1.0.jar', 'f-1.0.jar']
        "ext 'war'"                     | [ext: 'war']                   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'd-1.0.ear', 'e-1.0.jar', 'f-1.0.jar']
        "type 'war' and conf 'default'" | [type: 'war', conf: 'default'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'd-1.0.ear', 'e-1.0.jar', 'f-1.0.jar']
        "ext 'jar'"                     | [ext: 'jar']                   | ['a-1.0.jar', 'c-1.0.jar', 'd-1.0.war', 'd-1.0.ear', 'e-1.0.jar', 'f-1.0.war']
    }

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

        ivyRepo.module("org.gradle.test", "a", "1.0")
                .dependsOn("org.gradle.dep", "dep_module", "1.134")
                .withXml({
            asNode().dependencies[0].dependency.each { dep ->
                dep.appendNode("exclude", excludeAttributes)
            }
        })
                .publish()

        when:
        succeeds "check"

        then:
        def jars = ['a-1.0.jar'] + transitiveJars
        assertResolvedFiles(jars)

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
            asNode().dependencies[0].dependency.each { dep ->
                dep.appendNode("exclude", [module: "d"])
            }
        })
                .publish()
        ivyRepo.module("b")
                .dependsOn("c")
                .withXml({
            asNode().dependencies[0].dependency.each { dep ->
                dep.appendNode("exclude", [module: "d"])
                dep.appendNode("exclude", [module: "e"])
            }
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

    private void addExcludeRuleToModuleDependency(IvyModule module, String dependencyName, Map<String, String> excludeAttributes) {
        module.withXml {
            asNode().dependencies[0].dependency.findAll { it.@name == dependencyName }.each { Node moduleDependency ->
                moduleDependency.appendNode(EXCLUDE_ATTRIBUTE, excludeAttributes)
            }
        }
    }
}
