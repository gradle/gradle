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
 * Demonstrates the use of Ivy module excludes.
 *
 * @see <a href="http://ant.apache.org/ivy/history/latest-milestone/ivyfile/exclude.html">Ivy reference documentation</a>
 */
@Issue("https://issues.gradle.org/browse/GRADLE-3147")
class IvyDescriptorModuleExcludeResolveIntegrationTest extends AbstractIvyDescriptorExcludeResolveIntegrationTest {
    /**
     * Module exclude for dependencies having single artifact by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     */
    @Unroll
    def "module exclude having single artifact with #name"() {
        given:
        ivyRepo.module('b').publish()
        ivyRepo.module('c').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModule(moduleA, excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                     | excludeAttributes   | resolvedJars
        'non-matching module'    | [module: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'non-matching artifact'  | [artifact: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching all modules'   | [module: '*']       | ['a-1.0.jar']
        'matching module'        | [module: 'b']       | ['a-1.0.jar', 'c-1.0.jar']
        'matching all artifacts' | [artifact: '*']     | ['a-1.0.jar']
        'matching artifact'      | [artifact: 'b']     | ['a-1.0.jar', 'c-1.0.jar']
    }

    /**
     * Module exclude of transitive dependency with a single artifact by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> e
     */
    @Unroll
    def "module exclude for transitive dependency having single artifact with #name"() {
        given:
        ivyRepo.module('d').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModule(moduleA, excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                     | excludeAttributes   | resolvedJars
        'non-matching module'    | [module: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
        'non-matching artifact'  | [artifact: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
        'matching all modules'   | [module: '*']       | ['a-1.0.jar']
        'matching module'        | [module: 'd']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching all artifacts' | [artifact: '*']     | ['a-1.0.jar']
        'matching artifact'      | [artifact: 'd']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
    }

    /**
     * Module exclude of transitive dependency with a single artifact does not exclude its transitive module by using a combination of artifact exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d -> f
     * c -> e
     */
    @Unroll
    def "module exclude for transitive dependency having single artifact with #name does not exclude its transitive module"() {
        given:
        ivyRepo.module('f').publish()
        ivyRepo.module('d').dependsOn('f').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModule(moduleA, excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                     | excludeAttributes   | resolvedJars
        'non-matching artifact'  | [artifact: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'matching all artifacts' | [artifact: '*']     | ['a-1.0.jar']
        'matching artifact'      | [artifact: 'd']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
    }

    /**
     * Module exclude of transitive dependency with multiple artifacts by using a combination of artifact exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> e
     */
    @Unroll
    def "module exclude for transitive dependency having multiple artifacts with #name"() {
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
        addExcludeRuleToModule(moduleA, excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                         | excludeAttributes            | resolvedJars
        'non-matching module'        | [module: 'other']            | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'non-matching artifact'      | [artifact: 'other']          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'matching all modules'       | [module: '*']                | ['a-1.0.jar']
        'matching module'            | [module: 'd']                | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching all artifacts'     | [artifact: '*']              | ['a-1.0.jar']
        'matching artifact'          | [artifact: 'd']              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'matching artifact and type' | [artifact: 'd', type: 'jar'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
    }

    /**
     * Transitive module exclude for a module reachable via alternative path using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     */
    @Unroll
    def "module with #name is not excluded if reachable via alternate path"() {
        given:
        ivyRepo.module('d').publish()
        IvyModule moduleB = ivyRepo.module('b').dependsOn('d')
        addExcludeRuleToModule(moduleB, excludeAttributes)
        moduleB.publish()
        ivyRepo.module('c').dependsOn('d').publish()
        ivyRepo.module('a').dependsOn('b').dependsOn('c').publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar'])

        where:
        name                     | excludeAttributes
        'non-matching module'    | [module: 'other']
        'non-matching artifact'  | [artifact: 'other']
        'matching all modules'   | [module: '*']
        'matching module'        | [module: 'd']
        'matching all artifacts' | [artifact: '*']
        'matching artifact'      | [artifact: 'd']
    }

    /**
     * Transitive module exclude for module reachable by multiple paths for all paths by using a combination of exclude rules.
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
        addExcludeRuleToModule(moduleA, excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                     | excludeAttributes   | resolvedJars
        'non-matching module'    | [module: 'other']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'non-matching artifact'  | [artifact: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching all modules'   | [module: '*']       | ['a-1.0.jar']
        'matching all artifacts' | [artifact: '*']     | ['a-1.0.jar']
        'matching module'        | [module: 'd']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching artifact'      | [artifact: 'd']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
    }

    /**
     * Transitive module exclude for module reachable by multiple paths for all paths intersection of exclude rules.
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
        IvyModule moduleB = ivyRepo.module('b').dependsOn('d')
        IvyModule moduleC = ivyRepo.module('c').dependsOn('d')
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')

        excludeRulesPath1.each { excludeAttributes ->
            addExcludeRuleToModule(moduleB, excludeAttributes)
        }

        excludeRulesPath2.each { excludeAttributes ->
            addExcludeRuleToModule(moduleC, excludeAttributes)
        }

        moduleB.publish()
        moduleC.publish()
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                    | excludeRulesPath1                                                                  | excludeRulesPath2                         | resolvedJars
        'non-matching module'   | [[org: 'org.company', module: 'd'], [org: 'org.gradle.test', module: 'e']]         | [[org: 'org.company', module: 'd']]       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'non-matching artifact' | [[org: 'org.company', artifact: 'd'], [org: 'org.gradle.test', artifact: 'e']]     | [[org: 'org.company', artifact: 'd']]     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching module'       | [[org: 'org.gradle.test', module: 'd'], [org: 'org.gradle.test', module: 'e']]     | [[org: 'org.gradle.test', module: 'd']]   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching artifact'     | [[org: 'org.gradle.test', artifact: 'd'], [org: 'org.gradle.test', artifact: 'e']] | [[org: 'org.gradle.test', artifact: 'd']] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
    }

    /**
     * Module exclude of transitive dependency for union of multiple rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d -> f
     * c -> e
     */
    @Unroll
    def "transitive module exclude for union of multiple rules with #name"() {
        given:
        ivyRepo.module('f').publish()
        ivyRepo.module('d').dependsOn('f').publish()
        ivyRepo.module('b').dependsOn('d').publish()
        ivyRepo.module('e').publish()
        ivyRepo.module('c').dependsOn('e').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')

        excludeRules.each { excludeAttributes ->
            addExcludeRuleToModule(moduleA, excludeAttributes)
        }

        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name               | excludeRules                                                  | resolvedJars
        'no match'         | [[artifact: 'other'], [artifact: 'some'], [artifact: 'more']] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'all matches'      | [[artifact: 'b'], [artifact: 'd'], [artifact: 'f']]           | ['a-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'partial match'    | [[artifact: 'other'], [artifact: 'd'], [artifact: 'more']]    | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'duplicated match' | [[artifact: 'f'], [artifact: 'some'], [artifact: 'f']]        | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
    }

    private void addExcludeRuleToModule(IvyModule module, Map<String, String> excludeAttributes) {
        module.withXml {
            asNode().dependencies[0].appendNode(EXCLUDE_ATTRIBUTE, excludeAttributes)
        }
    }
}
