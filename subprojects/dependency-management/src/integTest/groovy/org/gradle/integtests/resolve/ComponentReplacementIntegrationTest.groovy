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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestDependency
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

class ComponentReplacementIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            configurations { conf }
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
            task resolvedFiles {
                dependsOn 'dependencies'
                doLast {
                    copy {
                        from configurations.conf
                        into 'resolved-files'
                    }
                    println "All files:"
                    configurations.conf.each { println it.name }
                }
            }
        """
        new ResolveTestFixture(buildFile).addDefaultVariantDerivationStrategy()
    }

    //publishes and declares the dependencies
    void declaredDependencies(String... deps) {
        publishedMavenModules(deps)
        def content = ''
        deps.each {
            content += "dependencies.conf '${new TestDependency(it).notation}'\n"
        }
        buildFile << """
            $content
        """
    }

    void declaredReplacements(String... reps) {
        def content = ''
        reps.each {
            def d = new TestDependency(it)
            content += "dependencies.modules.module('${d.group}:${d.name}') { replacedBy '${d.pointsTo.group}:${d.pointsTo.name}' }\n"
        }
        buildFile << """
            $content
        """
    }

    void declaredReplacementWithReason(String rep, String reason) {
        def d = new TestDependency(rep)
        buildFile << """
            dependencies.modules.module('${d.group}:${d.name}') { replacedBy '${d.pointsTo.group}:${d.pointsTo.name}', '$reason' }
        """
    }

    void resolvedFiles(String... files) {
        run("resolvedFiles")
        assert file('resolved-files').listFiles()*.name as Set == files as Set
    }

    void resolvedModules(String... modules) {
        resolvedFiles(modules.collect { new TestDependency(it).jarName } as String[])
    }

    def "ignores replacement if not in graph"() {
        declaredDependencies 'a'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'a'
    }

    def "ignores replacement if org does not match"() {
        declaredDependencies 'a', 'com:b'
        declaredReplacements 'a->org:b'
        expect:
        resolvedModules 'a', 'com:b'
    }

    def "just uses replacement if source not in graph"() {
        declaredDependencies 'b'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'b'
    }

    def "replaces already resolved module"() {
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'b'
    }

    def "replaces not yet resolved module"() {
        declaredDependencies 'b', 'a'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'b'
    }

    def "uses highest when it is last"() {
        declaredDependencies 'b', 'a', 'b:2'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'b:2'
    }

    def "uses highest when it is last following replacedBy"() {
        declaredDependencies 'a', 'b', 'b:2'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'b:2'
    }

    def "uses highest when it is first"() {
        declaredDependencies 'b:2', 'b', 'a'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'b:2'
    }

    def "uses highest when it is first followed by replacedBy"() {
        declaredDependencies 'b:2', 'b', 'a'
        declaredReplacements 'a->b'
        expect:
        resolvedModules 'b:2'
    }

    def "evicts transitive dependencies of replaced module"() {
        declaredDependencies 'a', 'c'
        declaredReplacements 'a->e'
        //resolution sequence: a,c,b,d,e!
        publishedMavenModules 'a->b', 'c->d', 'd->e'
        expect:
        resolvedModules 'c', 'd', 'e' //'b' is evicted
    }

    def "replaces transitive module"() {
        declaredDependencies 'a', 'c'
        declaredReplacements 'b->d'
        publishedMavenModules 'a->b', 'c->d'
        expect:
        resolvedModules 'a', 'd', 'c'
    }

    def "replaces module even if it was already conflict-resolved"() {
        declaredDependencies 'a:1', 'a:2'
        declaredReplacements 'a->c'
        //resolution sequence: a1,a2,!,b,c,!
        publishedMavenModules 'a:2->b', 'b->c'
        expect:
        resolvedModules 'c'
    }

    def "uses already resolved highest version"() {
        declaredDependencies 'a:1', 'a:2'
        declaredReplacements 'c->a'
        //resolution sequence: a1,a2,!,b,c,!
        publishedMavenModules 'a:2->b', 'b->c'
        expect:
        resolvedModules 'a:2', 'b'
    }

    def "latest replacement wins"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->b', 'a->c' //2 replacements for the same source module
        expect:
        resolvedModules 'c', 'b'
    }

    def "supports consecutive replacements"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->b', 'b->c'
        expect:
        resolvedModules 'c'
    }

    def "reports replacement cycles early"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->b', 'b->c', 'c->a'
        expect:
        def failure = fails()
        failure.assertHasCause("Cannot declare module replacement org:c->org:a because it introduces a cycle: org:c->org:a->org:b->org:c")
    }

    def "replacement target unresolved"() {
        publishedMavenModules('a')
        buildFile << "dependencies { conf 'org:a:1', 'org:b:1' }\n"
        declaredReplacements 'a->b'

        expect:
        fails("resolvedFiles")
        failure.assertHasCause("Could not resolve all files for configuration ':conf'.")
        failure.assertHasCause("Could not find org:b:1")
    }

    def "replacement source unresolved"() {
        publishedMavenModules('a')
        buildFile << "dependencies { conf 'org:a:1', 'org:b:1' }\n"
        declaredReplacements 'a->b'

        expect:
        fails("resolvedFiles")
        failure.assertHasCause("Could not resolve all files for configuration ':conf'.")
        failure.assertHasCause("Could not find org:b:1")
    }

    def "human error in declaring replacements is neatly reported"() {
        buildFile << """
            dependencies.modules.module('org:foo') { replacedBy('org:bar:2.0') }
        """

        expect:
        fails().assertHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org:bar:2.0")
    }

    def "human error in referring to component module metadata is neatly reported"() {
        buildFile << """
            dependencies.modules.module('org:foo:1.0') {}
        """

        expect:
        fails().assertHasCause("Cannot convert the provided notation to an object of type ModuleIdentifier: org:foo:1.0")
    }

    def "replacement target is not used if it is excluded"() {
        declaredDependencies 'a', 'b->c'
        declaredReplacements 'a->c'
        buildFile << "configurations.all { exclude module: 'c' }"
        expect:
        resolvedModules 'a', 'b'
    }

    def "replacement target is used if replacement source is excluded"() {
        declaredDependencies 'a', 'b->c'
        declaredReplacements 'a->c'
        buildFile << "configurations.all { exclude module: 'a' }"
        expect:
        resolvedModules 'c', 'b'
    }

    def "replacement is not used when it replaced by resolve rule"() {
        publishedMavenModules('d')
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->b'
        buildFile << """
            configurations.all { resolutionStrategy.eachDependency { dep ->
                if (dep.requested.name == 'b') {
                    dep.useTarget 'org:d:1'
                }
            }}
        """
        expect:
        resolvedModules 'a', 'd'
    }

    def "replacement and resolve rule have exactly the same target"() {
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->b'
        buildFile << """
            configurations.all { resolutionStrategy.eachDependency { dep ->
                if (dep.requested.name == 'a') {
                    dep.useTarget 'org:b:1'
                }
            }}
        """
        expect:
        resolvedModules 'b'
    }

    def "replacement is used when it is pulled to the graph via resolve rule"() {
        publishedMavenModules('d')
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->d'
        buildFile << """
            configurations.all { resolutionStrategy.eachDependency { dep ->
                if (dep.requested.name == 'b') {
                    dep.useTarget 'org:d:1'
                }
            }}
        """
        expect:
        resolvedModules 'd'
    }

    def "both source and replacement target are pulled to the graph via resolve rule"() {
        publishedMavenModules('a', 'b')
        declaredDependencies 'c', 'd'
        declaredReplacements 'a->b'
        buildFile << """
            configurations.all { resolutionStrategy.eachDependency { dep ->
                if (dep.requested.name == 'c') { dep.useTarget 'org:a:1' }
                if (dep.requested.name == 'd') { dep.useTarget 'org:b:1' }
            }}
        """
        expect:
        resolvedModules 'b'
    }

    def "replacement target is manipulated by resolve rule and then replaced again by different module replacement declaration"() {
        publishedMavenModules 'd'
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->b', 'c->d'
        buildFile << """
            configurations.all { resolutionStrategy.eachDependency { dep ->
                if (dep.requested.name == 'b') { dep.useTarget 'org:d:1' }
            }}
        """
        expect:
        resolvedModules 'a', 'd'
    }

    def "replacement target forms a legal cycle with resolve rule"() {
        publishedMavenModules 'd'
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->b', 'd->a'
        buildFile << """
            configurations.all { resolutionStrategy.eachDependency { dep ->
                if (dep.requested.name == 'b') { dep.useTarget 'org:d:1' }
            }}
        """
        //a->b->d->a
        expect:
        resolvedModules 'a'
    }

    def "supports multiple replacement targets"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->b', 'a->c'
        expect:
        resolvedModules 'b', 'c'
    }

    def "multiple source modules have the same replacement target"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->c', 'b->c'
        expect:
        resolvedModules 'c'
    }

    def "multiple source modules but only some are included in graph"() {
        declaredDependencies 'a', 'c'
        declaredReplacements 'a->c', 'b->c'
        expect:
        resolvedModules 'c'
    }

    def "declared modules coexist with forced versions"() {
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->b'
        buildFile << "configurations.all { resolutionStrategy.force 'org:a:1', 'org:b:1'} "
        expect:
        resolvedModules 'b'
    }

    @Ignore
    //maybe, as a way to 'clear' any replacements
    def "allow replacing with self"() {
        declaredDependencies 'a', 'b', 'c'
        declaredReplacements 'a->c', 'a->b', 'a->a'
        expect:
        resolvedModules 'a', 'b', 'c'
    }

    @Ignore
    def "pulls extra dependency to graph if multiple replacement targets declared"() {
        publishedMavenModules 'c'
        declaredDependencies 'a', 'b'
        declaredReplacements 'a->b', 'a->c'
        expect:
        resolvedModules 'b', 'c'
    }

    @Issue("https://github.com/gradle/gradle/issues/1472")
    def "handles '+' suffix in module name"() {
        declaredDependencies 'org:foo+', 'org:bar'
        declaredReplacements 'org:foo+->org:bar'
        expect:
        resolvedModules 'org:bar'
    }

    @Unroll
    def "can provide custom replacement reason"() {
        declaredDependencies 'a', 'b'
        if (custom) {
            declaredReplacementWithReason('a->b', 'A replaced with B')
        } else {
            declaredReplacements('a->b')
        }

        when:
        buildFile << """
            task check {
                doLast {
                    def modules = configurations.conf.incoming.resolutionResult.allComponents.findAll { it.id instanceof ModuleComponentIdentifier } as List
                    assert modules.find { it.id.module == 'b' }.selectionReason.description == "$expected"
                }
            }
        """

        then:
        resolvedModules 'b'

        when:
        run 'dependencyInsight', '--configuration=conf', '--dependency=a'

        then:
        result.groupedOutput.task(':dependencyInsight').output.contains("""org:b:1
   variant "runtime" [
      org.gradle.status             = release (not requested)
      org.gradle.usage              = java-runtime (not requested)
      org.gradle.component.category = library (not requested)
   ]
   Selection reasons:
      - Selected by rule : $expected

org:a:1 -> org:b:1""")

        when:
        run 'check'

        then:
        noExceptionThrown()

        where:
        custom | expected
        false  | "org:a replaced with org:b"
        true   | "A replaced with B"
    }

}
