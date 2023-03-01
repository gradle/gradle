/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories

import groovy.json.JsonSlurper
import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class NormalizingExcludeFactoryTest extends Specification implements ExcludeTestSupport {

    def setup() {
        factory = new NormalizingExcludeFactory(factory)
    }

    @Shared
    private DefaultIvyArtifactName artifactName = new DefaultIvyArtifactName("a", "b", "c")

    @Unroll("#left ∪ #right = #expected")
    def "union of two elements"() {
        expect:
        factory.anyOf(left, right) == expected

        and: "union is commutative"
        factory.anyOf(right, left) == expected

        where:
        left                                                                | right                                       | expected
        everything()                                                        | nothing()                                   | everything()
        everything()                                                        | everything()                                | everything()
        nothing()                                                           | nothing()                                   | nothing()
        everything()                                                        | group("foo")                                | everything()
        nothing()                                                           | group("foo")                                | group("foo")
        group("foo")                                                        | group("bar")                                | groupSet("foo", "bar")
        group("foo")                                                        | module("bar")                               | anyOf(group("foo"), module("bar"))
        anyOf(group("foo"), group("bar"))                                   | group("foo")                                | groupSet("foo", "bar")
        anyOf(group("foo"), module("bar"))                                  | module("bar")                               | anyOf(module("bar"), group("foo"))
        moduleId("org", "a")                                                | moduleId("org", "b")                        | moduleIdSet(["org", "a"], ["org", "b"])
        module("org")                                                       | module("org2")                              | moduleSet("org", "org2")
        groupSet("org", "org2")                                             | groupSet("org3", "org4")                    | groupSet("org", "org2", "org3", "org4")
        moduleSet("mod", "mod2")                                            | moduleSet("mod3", "mod4")                   | moduleSet("mod", "mod2", "mod3", "mod4")
        moduleIdSet(["org", "foo"], ["org", "bar"])                         | moduleIdSet(["org", "baz"], ["org", "quz"]) | moduleIdSet(["org", "foo"], ["org", "bar"], ["org", "baz"], ["org", "quz"])
        module("mod")                                                       | moduleSet("m1", "m2")                       | moduleSet("m1", "m2", "mod")
        group("g1")                                                         | groupSet("g2", "g3")                        | groupSet("g1", "g2", "g3")
        moduleId("g1", "m1")                                                | moduleIdSet(["g2", "m2"], ["g3", "m3"])     | moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"])

        moduleId("g1", "m1")                                                | module("m1")                                | module("m1")
        moduleIdSet(["g1", "m1"], ["g2", "m2"])                             | module("m1")                                | anyOf(module("m1"), moduleId("g2", "m2"))
        moduleIdSet(["g1", "m1"], ["g2", "m1"])                             | module("m1")                                | module("m1")
        moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"])               | module("m1")                                | anyOf(module("m1"), moduleIdSet(["g2", "m2"], ["g3", "m3"]))

        moduleId("g1", "m1")                                                | group("g1")                                 | group("g1")
        moduleIdSet(["g1", "m1"], ["g2", "m2"])                             | group("g1")                                 | anyOf(group("g1"), moduleId("g2", "m2"))
        moduleIdSet(["g1", "m1"], ["g1", "m2"])                             | group("g1")                                 | group("g1")
        moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"])               | group("g1")                                 | anyOf(group("g1"), moduleIdSet(["g2", "m2"], ["g3", "m3"]))

        moduleId("g1", "m1")                                                | moduleSet("m1", "m2")                       | moduleSet("m1", "m2")
        moduleIdSet(["g1", "m1"], ["g2", "m2"])                             | moduleSet("m1", "m2")                       | moduleSet("m1", "m2")
        moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"])               | moduleSet("m1", "m2")                       | anyOf(moduleId("g3", "m3"), moduleSet("m1", "m2"))
        moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"], ["g4", "m4"]) | moduleSet("m1", "m2")                       | anyOf(moduleIdSet(["g3", "m3"], ["g4", "m4"]), moduleSet("m1", "m2"))

        moduleId("g1", "m1")                                                | groupSet("g1", "g2")                        | groupSet("g1", "g2")
        moduleIdSet(["g1", "m1"], ["g2", "m2"])                             | groupSet("g1", "g2")                        | groupSet("g1", "g2")
        moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"])               | groupSet("g1", "g2")                        | anyOf(moduleId("g3", "m3"), groupSet("g1", "g2"))
        moduleIdSet(["g1", "m1"], ["g2", "m2"], ["g3", "m3"], ["g4", "m4"]) | groupSet("g1", "g2")                        | anyOf(moduleIdSet(["g3", "m3"], ["g4", "m4"]), groupSet("g1", "g2"))

    }

    @Unroll("#one ∪ #two ∪ #three = #expected")
    def "union of three elements"() {
        expect:
        [one, two, three].combinations().each { list ->
            assert factory.anyOf(list as Set) == expected
        }

        where:
        one          | two          | three        | expected
        everything() | nothing()    | nothing()    | everything()
        everything() | everything() | everything() | everything()
        nothing()    | nothing()    | nothing()    | nothing()
        everything() | group("foo") | everything() | everything()
        group("foo") | group("bar") | group("baz") | groupSet("foo", "bar", "baz")
    }

    @Unroll("#left ∩ #right = #expected")
    def "intersection of two elements"() {
        expect:
        factory.allOf(left, right) == expected

        and: "intersection is commutative"
        factory.allOf(right, left) == expected

        where:
        left                               | right                 | expected
        everything()                       | nothing()             | nothing()
        everything()                       | everything()          | everything()
        nothing()                          | nothing()             | nothing()
        everything()                       | group("foo")          | group("foo")
        nothing()                          | group("foo")          | nothing()
        group("foo")                       | group("foo")          | group("foo")
        group("foo")                       | module("bar")         | moduleId("foo", "bar")
        groupSet("foo", "foz")             | module("bar")         | moduleIdSet("foo:bar", "foz:bar")
        groupSet("foo", "foz")             | moduleSet("bar", "baz")         | moduleIdSet("foo:bar", "foz:bar", "foo:baz", "foz:baz")
        allOf(group("foo"), group("foo2")) | module("bar")         | nothing()
        allOf(group("foo"), module("bar")) | module("bar")         | moduleId("foo", "bar")
        moduleSet("m1", "m2", "m3")        | moduleSet("m1", "m3") | moduleSet("m1", "m3")
    }

    @Unroll("#one ∩ #two ∩ #three = #expected")
    def "intersection of three elements"() {
        expect:
        [one, two, three].combinations().each { list ->
            assert factory.allOf(list as Set) == expected
        }

        where:
        one                                                   | two                                         | three                                                                      | expected
        everything()                                          | nothing()                                   | nothing()                                                                  | nothing()
        everything()                                          | everything()                                | everything()                                                               | everything()
        nothing()                                             | nothing()                                   | nothing()                                                                  | nothing()
        everything()                                          | group("foo")                                | everything()                                                               | group("foo")
        group("foo")                                          | group("bar")                                | group("baz")                                                               | nothing()
        group("foo")                                          | module("bar")                               | groupSet("foo", "bar")                                                     | moduleId("foo", "bar")
        moduleId("org", "foo")                                | ivy("org", "mod", artifact("mod"), "exact") | group("org")                                                               | allOf(moduleId("org", "foo"), ivy("org", "mod", artifact("mod"), "exact"))
        anyOf(moduleId("org", "foo"), moduleId("org", "bar")) | ivy("org", "mod", artifact("mod"), "exact") | anyOf(group("org"), module("bar"))                                         | allOf(moduleIdSet(["org", "foo"], ["org", "bar"]), ivy("org", "mod", artifact("mod"), "exact"))
        anyOf(moduleId("org", "foo"), moduleId("org", "bar")) | ivy("org", "mod", artifact("mod"), "exact") | anyOf(moduleId("org", "foo"), module("bar"))                               | allOf(moduleIdSet(["org", "foo"], ["org", "bar"]), ivy("org", "mod", artifact("mod"), "exact"))
        anyOf(moduleId("org", "foo"), moduleId("org", "bar")) | ivy("org", "mod", artifact("mod"), "exact") | anyOf(moduleId("org", "foo"), ivy("org", "mod", artifact("mod"), "exact")) | allOf(anyOf(moduleId("org", "foo"), moduleId("org", "bar")), ivy("org", "mod", artifact("mod"), "exact"))
    }

    def "adhoc verification of code generated from ExcludeJsonLogToCode"() {

        def operand0 = moduleId('shake', 'grain')
        def operand1 = moduleId('root', 'rain')
        def operand2 = moduleId('cover', 'cover')
        def operand3 = moduleId('crib', 'lunchroom')
        def operand4 = moduleId('root', 'sort')
        def operand5 = moduleId('crib', 'building')
        def operand6 = moduleId('fact', 'grass')
        def operand7 = moduleId('crib', 'planes')
        def operand8 = moduleId('stove', 'pull')
        def operand9 = moduleId('calculator', 'suggestion')
        def operand10 = moduleId('beginner', 'plough')
        def operand11 = moduleId('insurance', 'hat')
        def operand12 = moduleId('toys', 'plant')
        def operand13 = moduleId('trail', 'wing')
        def operand14 = moduleId('ring', 'desk')
        def operand15 = moduleId('yak', 'teaching')
        def operand16 = moduleId('street', 'cattle')
        def operand17 = group('sun')
        def operand18 = moduleId('crib', 'wealth')
        def operand19 = moduleId('cabbage', 'playground')
        def operand20 = moduleId('wren', 'flowers')
        def operand21 = moduleId('insurance', 'cobweb')
        def operand22 = moduleId('crib', 'shame')
        def operand23 = moduleId('plate', 'authority')
        def operand24 = moduleId('stove', 'cave')
        def operand25 = moduleId('floor', 'shelf')
        def operand26 = moduleId('snakes', 'snakes')
        def operand27 = moduleId('crib', 'ants')
        def operand28 = moduleId('comparison', 'comparison')
        def operand29 = moduleId('quicksand', 'eyes')
        def operand30 = moduleId('crib', 'thumb')
        def operand31 = module('church')
        def operand32 = moduleId('needle', 'celery')
        def operand33 = moduleId('crib', 'competition')
        def operand34 = moduleId('metal', 'box')
        def operand35 = moduleId('root', 'industry')
        def operand36 = group('brother')
        def operand37 = moduleId('crib', 'deer')
        def operand38 = moduleId('root', 'waves')
        def operand39 = moduleId('advice', 'acoustics')
        def operand40 = moduleId('ring', 'nut')
        def operand41 = moduleId('store', 'finger')
        def operand42 = moduleId('plate', 'null')
        def operand43 = moduleId('crib', 'word65')
        def operand44 = moduleId('stove', 'word66')
        def operand45 = moduleId('word67', 'word68')
        def operand46 = moduleId('word69', 'word70')
        def operand47 = moduleId('word69', 'word71')
        def operand48 = moduleId('shake', 'finger')
        def operand49 = moduleId('word72', 'word73')
        def operand50 = moduleId('crib', 'word74')
        def operand51 = moduleId('word75', 'word76')
        def operand52 = moduleId('word77', 'word78')
        def operand53 = moduleId('word79', 'word80')
        def operand54 = moduleId('stove', 'word81')
        def operand55 = moduleId('word82', 'word83')
        def operand56 = moduleId('word84', 'word85')
        def operand57 = moduleId('stove', 'word86')
        def operand58 = moduleId('root', 'word87')
        def operand59 = moduleId('root', 'word88')
        def operand60 = moduleId('root', 'word89')
        def operand61 = moduleId('root', 'word90')
        def operation = factory.anyOf([
            operand0,
            operand1,
            operand2,
            operand3,
            operand4,
            operand5,
            operand6,
            operand7,
            operand8,
            operand9,
            operand10,
            operand11,
            operand12,
            operand13,
            operand14,
            operand15,
            operand16,
            operand17,
            operand18,
            operand19,
            operand20,
            operand21,
            operand22,
            operand23,
            operand24,
            operand25,
            operand26,
            operand27,
            operand28,
            operand29,
            operand30,
            operand31,
            operand32,
            operand33,
            operand34,
            operand35,
            operand36,
            operand37,
            operand38,
            operand39,
            operand40,
            operand41,
            operand42,
            operand43,
            operand44,
            operand45,
            operand46,
            operand47,
            operand48,
            operand49,
            operand50,
            operand51,
            operand52,
            operand53,
            operand54,
            operand55,
            operand56,
            operand57,
            operand58,
            operand59,
            operand60,
            operand61
        ] as Set)


        expect:
        operation
    }

    @Ignore("to be used adhoc when analyzing specific issue")
    def 'replay content from shared json formatted log'() {
        def input = new File('/path/to/log.json')
        def converter = new ExcludeJsonLogToCode()

        def slurper = new JsonSlurper()

        def compilerConf = new CompilerConfiguration()
        compilerConf.scriptBaseClass = DelegatingScript.class.name

        def shell = new GroovyShell(this.class.classLoader, new Binding(), compilerConf)

        expect:
        input.readLines().eachWithIndex { line, index ->
            def parsedLine = slurper.parse(line.trim().getBytes("utf-8"))
            def script = shell.parse(converter.toCodeFromSlurpedJson(parsedLine))
            script.setDelegate(this)
            def result = script.run()

            assert "$index" + slurper.parse(result.toString().getBytes("utf-8")).toString() == "$index" + parsedLine.operation.result.toString()
        }
    }
}
