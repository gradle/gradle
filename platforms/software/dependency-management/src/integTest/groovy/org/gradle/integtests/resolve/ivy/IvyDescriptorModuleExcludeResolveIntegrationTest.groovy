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
        'non-matching artifact'  | [artifact: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching all artifacts' | [artifact: '*']     | ['a-1.0.jar']
        'matching artifact'      | [artifact: 'b']     | ['a-1.0.jar', 'c-1.0.jar']
        'matching self artifact' | [artifact: 'a']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar'] // Current behaviour, likely a bug
    }

    /**
     * Module exclude of transitive dependency with a single artifact by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> e
     */
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
        'non-matching artifact'  | [artifact: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
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
        'non-matching artifact'  | [artifact: 'other']
        'matching all artifacts' | [artifact: '*']
        'matching artifact'      | [artifact: 'd']
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
        def moduleB = ivyRepo.module('b').dependsOn('d')
        addExcludeRuleToModule(moduleB, excludeAttributes)
        moduleB.publish()
        def moduleC = ivyRepo.module('c').dependsOn('d')
        addExcludeRuleToModule(moduleC, excludeAttributes)
        moduleC.publish()
        ivyRepo.module('a').dependsOn('b').dependsOn('c').publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                     | excludeAttributes   | resolvedJars
        'non-matching artifact'  | [artifact: 'other'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'matching all artifacts' | [artifact: '*']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'matching artifact'      | [artifact: 'd']     | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
    }

    /**
     * When a module is depended on via multiple paths, it is excluded only if excluded on each of the paths.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     * d -> e
     */
    def "when a module is depended on via multiple paths, it is excluded only if excluded on each of the paths (#name)"() {
        given:
        ivyRepo.module('e').publish()
        ivyRepo.module('d').dependsOn('e').publish()
        IvyModule moduleB = ivyRepo.module('b').dependsOn('d')
        IvyModule moduleC = ivyRepo.module('c').dependsOn('d')
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')

        addExcludeRuleToModule(moduleB, excludePath1)
        addExcludeRuleToModule(moduleC, excludePath2)

        moduleB.publish()
        moduleC.publish()
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                             | excludePath1    | excludePath2             | resolvedJars
        'non-matching group'             | [module: 'e']   | [org: 'org.other']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
        'non-matching module'            | [module: 'f']   | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
        'non-matching artifact'          | [artifact: 'f'] | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar', 'e-1.0.jar']
        'intervening group and module'   | [module: 'd']   | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'intervening group and artifact' | [artifact: 'd'] | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'leaf group and module'          | [module: 'e']   | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
        'leaf group and artifact'        | [artifact: 'e'] | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
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

        addExcludeRuleToModule(moduleB, excludePath1)
        addExcludeRuleToModule(moduleC, excludePath2)

        moduleB.publish()
        moduleC.publish()
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(resolvedJars)

        where:
        name                  | excludePath1    | excludePath2             | resolvedJars
        'excluded by module'  | [module: 'd']   | [module: 'e']            | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'exclude by artifact' | [artifact: 'd'] | [artifact: 'e']          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'excluded by group'   | [module: 'e']   | [org: 'org.gradle.test'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'not excluded'        | [module: 'e']   | [org: 'org.other']       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar']
    }

    /**
     * Exclusions with non-default ivy pattern matchers are not able to be simply merged.
     * This test checks that these can be combined successfully.
     *
     * Dependency graph:
     * a -> b, c
     * b -> d
     * c -> d
     * d -> e
     */
    @Issue("GRADLE-3275")
    def "can merge excludes with default and non-default ivy pattern matchers"() {
        given:
        ivyRepo.module('e').publish()

        IvyModule moduleD = ivyRepo.module('d').dependsOn('e')
        IvyModule moduleB = ivyRepo.module('b').dependsOn('d')
        IvyModule moduleC = ivyRepo.module('c').dependsOn('d')
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c').dependsOn('e')

        addExcludeRuleToModule(moduleA, [org: 'could.be.anything.1'])
        addExcludeRuleToModule(moduleD, [org: 'could.be.anything.4'])

        // These 2 rules are combined in a union
        addExcludeRuleToModule(moduleB, [module: 'e', matcher: 'regexp'])
        addExcludeRuleToModule(moduleC, [org: 'org.gradle.test'])

        moduleB.publish()
        moduleC.publish()
        moduleA.publish()
        moduleD.publish()

        expect:
        succeedsDependencyResolution()
    }

    /**
     * Ivy module exclusions may define one or more configurations to apply to.
     * Configuration exclusions are inherited.
     * Dependency graph:
     * a -> b, c
     */
    @Issue("GRADLE-3275")
    def "module excludes apply to specified configurations"() {
        given:
        IvyModule moduleA = ivyRepo.module('a')
            .configuration('other')
            .dependsOn('b').dependsOn('c').dependsOn('d')
        ivyRepo.module('b').publish()
        ivyRepo.module('c').publish()
        ivyRepo.module('d').publish()

        addExcludeRuleToModule(moduleA, [module: 'b', conf: 'other'])
        addExcludeRuleToModule(moduleA, [module: 'c', conf: 'runtime'])
        addExcludeRuleToModule(moduleA, [module: 'd', conf: 'default'])

        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(['a-1.0.jar', 'b-1.0.jar'])
    }

    @Issue("GRADLE-3951")
    def "artifact excludes merge correctly with chained composite exclusions"() {
        ivyRepo.module('a').dependsOn('b').dependsOn('c').publish()

        def moduleB = ivyRepo.module('b').dependsOn('d')
        addExcludeRuleToModule(moduleB, [artifact: 'e'])
        moduleB.publish()

        def moduleC = ivyRepo.module('c').dependsOn('d')
        addExcludeRuleToModule(moduleC, [artifact: 'e'])
        addExcludeRuleToModule(moduleC, [artifact: 'doesnotexist1', matcher: 'regexp'])
        moduleC.publish()

        def moduleD = ivyRepo.module('d').dependsOn('e')
        addExcludeRuleToModule(moduleD, [artifact: 'doesnotexist2', matcher: 'regexp'])
        moduleD.publish()

        ivyRepo.module('e').publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0.jar'])
    }

    private void addExcludeRuleToModule(IvyModule module, Map<String, String> excludeAttributes) {
        if (!excludeAttributes.containsKey("matcher")) {
            excludeAttributes.put("matcher", "exact")
        }
        module.withXml {
            asNode().dependencies[0].appendNode(EXCLUDE_ATTRIBUTE, excludeAttributes)
        }
    }
}
