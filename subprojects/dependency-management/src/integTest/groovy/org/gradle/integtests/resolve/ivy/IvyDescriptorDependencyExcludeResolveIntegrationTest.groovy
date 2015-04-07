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
import spock.lang.Unroll

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
    @Unroll
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
    @Unroll
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
    @Unroll
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
    @Unroll
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
    @Unroll
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
     * Transitive dependency exclude for a module reachable via alternative path using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    @Unroll
    def "module excluding #name is not excluded if reachable via path that does not exclude it"() {
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
        assertResolvedFiles(resolvedJars)

        where:
        name                    | excludeAttributes | resolvedJars
        'non-matching module'   | [module: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'non-matching artifact' | [name: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching all modules'  | [module: '*']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching module'       | [module: 'd']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching artifact'     | [name: 'd']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
    }

    /**
     * Transitive dependency exclude for a module reachable via alternative path using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    def "artifact is not excluded if reachable via path that does not exclude it"() {
        given:
        ivyRepo.module('d').artifact([type: 'war']).artifact([type: 'ear']) publish()
        def moduleB = ivyRepo.module('b').dependsOn('d')
        addExcludeRuleToModuleDependency(moduleB, 'd', [type: 'war'])
        moduleB.publish()
        def moduleC = ivyRepo.module('c').dependsOn('d')
        addExcludeRuleToModuleDependency(moduleC, 'd', [type: 'ear'])
        moduleC.publish()
        def moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.ear', 'd-1.0.war'])
    }

    /**
     * Transitive dependency exclude for module reachable by multiple paths for all paths by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    @Unroll
    def "module reachable by multiple paths excluded for all paths with #name"() {
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
     * Transitive dependency exclude for module reachable by multiple paths for all paths by intersection of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    @Unroll
    def "module reachable by multiple paths excluded for all paths with intersection of #name"() {
        given:
        ivyRepo.module('d').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('c').dependsOn('d').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')

        excludeRulesPath1.each { excludeAttributes ->
            addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        }

        excludeRulesPath2.each { excludeAttributes ->
            addExcludeRuleToModuleDependency(moduleA, 'c', excludeAttributes)
        }

        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                    | excludeRulesPath1                                                              | excludeRulesPath2                       | resolvedJars
        'non-matching module'   | [[org: 'org.company', module: 'd'], [org: 'org.gradle.test', module: 'e']]     | [[org: 'org.company', module: 'd']]     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'non-matching artifact' | [[org: 'org.company', name: 'd'], [org: 'org.gradle.test', name: 'e']]         | [[org: 'org.company', name: 'd']]       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching module'       | [[org: 'org.gradle.test', module: 'd'], [org: 'org.gradle.test', module: 'e']] | [[org: 'org.gradle.test', module: 'd']] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching artifact'     | [[org: 'org.gradle.test', name: 'd'], [org: 'org.gradle.test', name: 'e']]     | [[org: 'org.gradle.test', name: 'd']]   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
    }

    /**
     * Exclude of transitive dependency for union of multiple rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d -> f
     * c -> e
     */
    @Unroll
    def "transitive dependency exclude for union of multiple rules with #name"() {
        given:
        ivyRepo.module('f').publish()
        ivyRepo.module('d').dependsOn('f').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')

        excludeRules.each { excludeAttributes ->
            addExcludeRuleToModuleDependency(moduleA, 'b', excludeAttributes)
        }

        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name               | excludeRules                                      | resolvedJars
        'no match'         | [[name: 'other'], [name: 'some'], [name: 'more']] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'all matches'      | [[name: 'b'], [name: 'd'], [name: 'f']]           | ['a-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'partial match'    | [[name: 'other'], [name: 'd'], [name: 'more']]    | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'duplicated match' | [[name: 'f'], [name: 'some'], [name: 'f']]        | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
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
    @Unroll
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

    private void addExcludeRuleToModuleDependency(IvyModule module, String dependencyName, Map<String, String> excludeAttributes) {
        module.withXml {
            Node moduleDependency = asNode().dependencies[0].dependency.find { it.@name == dependencyName }
            assert moduleDependency, "Failed to find module dependency with name '$dependencyName'"
            moduleDependency.appendNode(EXCLUDE_ATTRIBUTE, excludeAttributes)
        }
    }
}
