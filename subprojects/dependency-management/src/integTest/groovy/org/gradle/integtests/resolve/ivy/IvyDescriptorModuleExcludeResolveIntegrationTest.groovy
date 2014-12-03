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
     * Module exclude for dependencies having single artifact by using a combination of exclude rules that only match partially or not at all.
     *
     * Dependency graph:
     * a -> b, c
     */
    @Unroll
    def "module exclude having single artifact with partially matching #name"() {
        given:
        ivyRepo.module('b').publish()
        ivyRepo.module('c').publish()
        IvyModule moduleA = ivyRepo.module('a').dependsOn('b').dependsOn('c')
        addExcludeRuleToModule(moduleA, excludeAttributes)
        moduleA.publish()

        when:
        succeedsDependencyResolution()

        then:
        assertResolvedFiles(['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar'])

        where:
        name                                  | excludeAttributes
        'module'                              | [module: 'other']
        'org and module'                      | [org: 'org.gradle.some', module: 'b']
        'artifact'                            | [artifact: 'other']
        'artifact and type'                   | [artifact: 'b', type: 'sources']
        'artifact and ext'                    | [artifact: 'b', ext: 'war']
        'artifact, type and ext'              | [artifact: 'b', type: 'javadoc', ext: 'jar']
        'org and artifact'                    | [org: 'org.gradle.test', artifact: 'other']
        'org, artifact and type'              | [org: 'org.gradle.test', artifact: 'b', type: 'sources']
        'org, artifact, type and ext'         | [org: 'org.gradle.test', artifact: 'b', type: 'javadoc', ext: 'jar']
        'org, module and artifact'            | [org: 'org.gradle.test', module: 'b', artifact: 'other']
        'org, module, artifact and type'      | [org: 'org.gradle.test', module: 'b', artifact: 'b', type: 'sources']
        'org, module, artifact, type and ext' | [org: 'org.gradle.test', module: 'b', artifact: 'b', type: 'jar', ext: 'war']
    }

    /**
     * Module exclude for dependencies having single artifact by using a combination of exclude rules.
     *
     * Dependency graph:
     * a -> b, c
     */
    @Unroll
    def "module exclude having single artifact with matching #name"() {
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
        name                                  | excludeAttributes                                                             | resolvedJars
        'all modules'                         | [module: '*']                                                                 | ['a-1.0.jar']
        'module'                              | [module: 'b']                                                                 | ['a-1.0.jar', 'c-1.0.jar']
        'org and all modules'                 | [org: 'org.gradle.test', module: '*']                                         | ['a-1.0.jar']
        'org and module'                      | [org: 'org.gradle.test', module: 'b']                                         | ['a-1.0.jar', 'c-1.0.jar']
        'all artifacts'                       | [artifact: '*']                                                               | ['a-1.0.jar']
        'wildcard artifact'                   | [artifact: 'b*']                                                              | ['a-1.0.jar', 'c-1.0.jar']
        'artifact'                            | [artifact: 'b']                                                               | ['a-1.0.jar', 'c-1.0.jar']
        'artifact and type'                   | [artifact: 'b', type: 'jar']                                                  | ['a-1.0.jar', 'c-1.0.jar']
        'artifact and ext'                    | [artifact: 'b', ext: 'jar']                                                   | ['a-1.0.jar', 'c-1.0.jar']
        'artifact, type and ext'              | [artifact: 'b', type: 'jar', ext: 'jar']                                      | ['a-1.0.jar', 'c-1.0.jar']
        'org and artifact'                    | [org: 'org.gradle.test', artifact: 'b']                                       | ['a-1.0.jar', 'c-1.0.jar']
        'org, artifact and type'              | [org: 'org.gradle.test', artifact: 'b', type: 'jar']                          | ['a-1.0.jar', 'c-1.0.jar']
        'org, artifact, type and ext'         | [org: 'org.gradle.test', artifact: 'b', type: 'jar', ext: 'jar']              | ['a-1.0.jar', 'c-1.0.jar']
        'org, module and artifact'            | [org: 'org.gradle.test', module: 'b', artifact: 'b']                          | ['a-1.0.jar', 'c-1.0.jar']
        'org, module, artifact and type'      | [org: 'org.gradle.test', module: 'b', artifact: 'b', type: 'jar']             | ['a-1.0.jar', 'c-1.0.jar']
        'org, module, artifact, type and ext' | [org: 'org.gradle.test', module: 'b', artifact: 'b', type: 'jar', ext: 'jar'] | ['a-1.0.jar', 'c-1.0.jar']
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
    def "module exclude for transitive dependency having single artifact with matching #name"() {
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
        name                                  | excludeAttributes                                                             | resolvedJars
        'all modules'                         | [module: '*']                                                                 | ['a-1.0.jar']
        'module'                              | [module: 'd']                                                                 | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org and all modules'                 | [org: 'org.gradle.test', module: '*']                                         | ['a-1.0.jar']
        'org and module'                      | [org: 'org.gradle.test', module: 'd']                                         | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'all artifacts'                       | [artifact: '*']                                                               | ['a-1.0.jar']
        'wildcard artifact'                   | [artifact: 'd*']                                                              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'artifact'                            | [artifact: 'd']                                                               | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'artifact and type'                   | [artifact: 'd', type: 'jar']                                                  | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'artifact and ext'                    | [artifact: 'd', ext: 'jar']                                                   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'artifact, type and ext'              | [artifact: 'd', type: 'jar', ext: 'jar']                                      | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org and artifact'                    | [org: 'org.gradle.test', artifact: 'd']                                       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org, artifact and type'              | [org: 'org.gradle.test', artifact: 'd', type: 'jar']                          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org, artifact, type and ext'         | [org: 'org.gradle.test', artifact: 'd', type: 'jar', ext: 'jar']              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org, module and artifact'            | [org: 'org.gradle.test', module: 'd', artifact: 'd']                          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org, module, artifact and type'      | [org: 'org.gradle.test', module: 'd', artifact: 'd', type: 'jar']             | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org, module, artifact, type and ext' | [org: 'org.gradle.test', module: 'd', artifact: 'd', type: 'jar', ext: 'jar'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
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
    def "module exclude for transitive dependency having single artifact with matching #name does not exclude its transitive module"() {
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
        name                                  | excludeAttributes                                                             | resolvedJars
        'all artifacts'                       | [artifact: '*']                                                               | ['a-1.0.jar']
        'wildcard artifact'                   | [artifact: 'd*']                                                              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'artifact'                            | [artifact: 'd']                                                               | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'artifact and type'                   | [artifact: 'd', type: 'jar']                                                  | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'artifact and ext'                    | [artifact: 'd', ext: 'jar']                                                   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'artifact, type and ext'              | [artifact: 'd', type: 'jar', ext: 'jar']                                      | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'org and artifact'                    | [org: 'org.gradle.test', artifact: 'd']                                       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'org, artifact and type'              | [org: 'org.gradle.test', artifact: 'd', type: 'jar']                          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'org, artifact, type and ext'         | [org: 'org.gradle.test', artifact: 'd', type: 'jar', ext: 'jar']              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'org, module and artifact'            | [org: 'org.gradle.test', module: 'd', artifact: 'd']                          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'org, module, artifact and type'      | [org: 'org.gradle.test', module: 'd', artifact: 'd', type: 'jar']             | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
        'org, module, artifact, type and ext' | [org: 'org.gradle.test', module: 'd', artifact: 'd', type: 'jar', ext: 'jar'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar', 'f-1.0.jar']
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
    def "module exclude for transitive dependency having multiple artifacts with matching #name"() {
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
        name                                  | excludeAttributes                                                             | resolvedJars
        'all modules'                         | [module: '*']                                                                 | ['a-1.0.jar']
        'module'                              | [module: 'd']                                                                 | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org and all modules'                 | [org: 'org.gradle.test', module: '*']                                         | ['a-1.0.jar']
        'org and module'                      | [org: 'org.gradle.test', module: 'd']                                         | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'all artifacts'                       | [artifact: '*']                                                               | ['a-1.0.jar']
        'artifact'                            | [artifact: 'd']                                                               | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'artifact and type'                   | [artifact: 'd', type: 'jar']                                                  | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'artifact and ext'                    | [artifact: 'd', ext: 'jar']                                                   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'artifact, type and ext'              | [artifact: 'd', type: 'jar', ext: 'jar']                                      | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'org and artifact'                    | [org: 'org.gradle.test', artifact: 'd']                                       | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org, artifact and type'              | [org: 'org.gradle.test', artifact: 'd', type: 'jar']                          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'org, artifact, type and ext'         | [org: 'org.gradle.test', artifact: 'd', type: 'jar', ext: 'jar']              | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'org, module and artifact'            | [org: 'org.gradle.test', module: 'd', artifact: 'd']                          | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'e-1.0.jar']
        'org, module, artifact and type'      | [org: 'org.gradle.test', module: 'd', artifact: 'd', type: 'jar']             | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
        'org, module, artifact, type and ext' | [org: 'org.gradle.test', module: 'd', artifact: 'd', type: 'jar', ext: 'jar'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar', 'd-1.0-javadoc.jar', 'd-1.0-sources.jar', 'e-1.0.jar']
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
    def "module with matching #name is not excluded if reachable via alternate path"() {
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
        name                  | excludeAttributes
        'all modules'         | [module: '*']
        'module'              | [module: 'd']
        'org and all modules' | [org: 'org.gradle.test', module: '*']
        'org and module'      | [org: 'org.gradle.test', module: 'd']
        'artifact'            | [artifact: 'd']
        'org and artifact'    | [org: 'org.gradle.test', artifact: 'd']
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
    def "module reachable by multiple paths excluded for all paths with matching #name"() {
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
        name                  | excludeAttributes                       | resolvedJars
        'all modules'         | [module: '*']                           | ['a-1.0.jar']
        'module'              | [module: 'd']                           | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'org and all modules' | [org: 'org.gradle.test', module: '*']   | ['a-1.0.jar']
        'org and module'      | [org: 'org.gradle.test', module: 'd']   | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'artifact'            | [artifact: 'd']                         | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
        'org and artifact'    | [org: 'org.gradle.test', artifact: 'd'] | ['a-1.0.jar', 'b-1.0.jar', 'c-1.0.jar']
    }

    private void addExcludeRuleToModule(IvyModule module, Map<String, String> excludeAttributes) {
        module.withXml {
            asNode().dependencies[0].appendNode(EXCLUDE_ATTRIBUTE, excludeAttributes)
        }
    }
}
