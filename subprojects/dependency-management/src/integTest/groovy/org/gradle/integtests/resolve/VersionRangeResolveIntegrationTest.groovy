/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import spock.lang.Unroll

/**
 * A comprehensive test of dependency resolution of a single module version, given a set of input selectors.
 * // TODO:DAZ This is a bit _too_ comprehensive, and has coverage overlap. Consolidate and streamline.
 */
class VersionRangeResolveIntegrationTest extends AbstractDependencyResolutionTest {

    static final FIXED_7 = fixed(7)
    static final FIXED_9 = fixed(9)
    static final FIXED_10 = fixed(10)
    static final FIXED_11 = fixed(11)
    static final FIXED_12 = fixed(12)
    static final FIXED_13 = fixed(13)
    static final RANGE_7_8 = range(7, 8)
    static final RANGE_10_11 = range(10, 11)
    static final RANGE_10_12 = range(10, 12)
    static final RANGE_10_14 = range(10, 14)
    static final RANGE_10_16 = range(10, 16)
    static final RANGE_11_12 = range(11, 12)
    static final RANGE_12_14 = range(12, 14)
    static final RANGE_13_14 = range(13, 14)
    static final RANGE_14_16 = range(14, 16)

    static final REJECT_11 = reject(11)
    static final REJECT_12 = reject(12)
    static final REJECT_13 = reject(13)

    def baseBuild
    def resolve = new ResolveTestFixture(buildFile, "conf")

    def setup() {
        (9..13).each {
            mavenRepo.module("org", "foo", "${it}").publish()
        }

        settingsFile << "rootProject.name = 'test'"
        buildFile << """
            repositories {
                maven { url '${mavenRepo.uri}' }
            }
            configurations {
                conf
            }
"""
        resolve.prepare()
        baseBuild = buildFile.text
    }

    @Unroll
    void "check behaviour with #dep1 and #dep2"() {
        given:
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()

        when:
        buildFile.text = baseBuild + """
            dependencies {
                conf('org:bar:${dep1}')
                conf('org:bar:${dep2}')
            }
        """
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:bar:${dep1}", "org:bar:${lenientResult}")
                edge("org:bar:${dep2}", "org:bar:${lenientResult}")
            }
        }
        when:
        // Invert the order of dependency declaration
        buildFile.text = baseBuild + """
            dependencies {
                conf('org:bar:${dep2}')
                conf('org:bar:${dep1}')
            }
        """
        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                edge("org:bar:${dep1}", "org:bar:${lenientResult}")
                edge("org:bar:${dep2}", "org:bar:${lenientResult}")
            }
        }

        when:
        // Declare versions with 'strictly'
        buildFile.text = baseBuild + """
            dependencies {
                conf('org:bar') {
                    version { strictly('${dep1}') }
                }
                conf('org:bar') {
                    version { strictly('${dep2}') }
                }
            }
        """

        then:
        // Cannot convert open range to 'strictly'
        if (strictResult == "FAIL" || !strictable(dep1) || !strictable(dep2)) {
            fails(":checkDeps")
        } else {
            succeeds(":checkDeps")
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:bar:${dep1}", "org:bar:${strictResult}")
                    edge("org:bar:${dep2}", "org:bar:${strictResult}")
                }
            }
        }

        when:
        // Use strict conflict resolution
        buildFile.text = baseBuild + """
            configurations.conf.resolutionStrategy.failOnVersionConflict()
            dependencies {
                conf('org:bar:${dep1}')
                conf('org:bar:${dep2}')
            }
        """

        then:
        if (strictResult == "FAIL") {
            fails(":checkDeps")
        } else {
            succeeds(":checkDeps")
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:bar:${dep1}", "org:bar:${strictResult}")
                    edge("org:bar:${dep2}", "org:bar:${strictResult}")
                }
            }
        }


        where:
        dep1         | dep2         | lenientResult | strictResult | correctLenient | correctStrict
        "1.0"        | "1.1"        | "1.1"         | "FAIL"       | ''             | ''
        "[1.0, 1.2]" | "1.1"        | "1.1"         | "1.1"        | ''             | ''
        "[1.0, 1.2]" | "[1.0, 1.1]" | "1.1"         | "1.1"        | ''             | ''
        "[1.0, 1.4]" | "1.1"        | "1.1"         | "1.1"        | ''             | ''
        "[1.0, 1.4]" | "[1.0, 1.1]" | "1.1"         | "1.1"        | ''             | ''
        "[1.0, 1.4]" | "[1.0, 1.6]" | "1.2"         | "1.2"        | ''             | ''
        "[1.0, )"    | "1.1"        | "1.1"         | "1.1"        | ''             | ''
        "[1.0, )"    | "[1.0, 1.1]" | "1.1"         | "1.1"        | ''             | ''
        "[1.0, )"    | "[1.0, 1.4]" | "1.2"         | "1.2"        | ''             | ''
        "[1.0, )"    | "[1.1, )"    | "1.2"         | "1.2"        | ''             | ''
        "[1.0, 2)"   | "1.1"        | "1.1"         | "1.1"        | ''             | ''
        "[1.0, 2)"   | "[1.0, 1.1]" | "1.1"         | "1.1"        | ''             | ''
        "[1.0, 2)"   | "[1.0, 1.4]" | "1.2"         | "1.2"        | ''             | ''
        "[1.0, 2)"   | "[1.1, )"    | "1.2"         | "1.2"        | ''             | ''
        "1.+"        | "[1.0, 1.4]" | "1.2"         | "1.2"        | ''             | ''
        "1.+"        | "[1.1, )"    | "1.2"         | "1.2"        | ''             | ''

        // Currently incorrect behaviour
        "1.+"        | "1.1"        | "1.2"         | "FAIL"       | "1.1"          | "1.1"  // #4180
        "1.+"        | "[1.0, 1.1]" | "1.2"         | "FAIL"       | "1.1"          | "1.1"  // #4180
    }

    private boolean strictable(String version) {
        return !version.endsWith(", )") && !version.endsWith("+")
    }

    @Unroll
    void "check behaviour with #dep1 and reject #reject"() {
        given:
        mavenRepo.module("org", "bar", "1.0").publish()
        mavenRepo.module("org", "bar", "1.1").publish()
        mavenRepo.module("org", "bar", "1.2").publish()

        when:
        buildFile.text = baseBuild + """
            dependencies {
                conf('org:bar:${dep1}')
                conf('org:bar') {
                    version { reject '${reject}' }
                }
            }
        """

        then:
        if (lenientResult == "FAIL") {
            fails ':checkDeps'
        } else {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:bar:${dep1}", "org:bar:${lenientResult}")
                    edge("org:bar", "org:bar:${lenientResult}")
                }
            }
        }

        when:
        // Invert the order of dependency declaration
        buildFile.text = baseBuild + """
            dependencies {
                conf('org:bar') {
                    version { reject '${reject}' }
                }
                conf('org:bar:${dep1}')
            }
        """

        then:
        if (lenientResult == "FAIL") {
            fails ':checkDeps'
        } else {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:bar:${dep1}", "org:bar:${lenientResult}")
                    edge("org:bar", "org:bar:${lenientResult}")
                }
            }
        }

        when:
        // Inverted order with a reject constraint
        buildFile.text = baseBuild + """
            dependencies {
                constraints {
                    conf('org:bar') {
                        version { reject '${reject}' }
                    }
                }
                conf('org:bar:${dep1}')
            }
        """

        then:
        if (lenientResult == "FAIL") {
            fails ':checkDeps'
        } else {
            run ':checkDeps'
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:bar:${dep1}", "org:bar:${lenientResult}")
                    edge("org:bar", "org:bar:${lenientResult}")
                }
            }
        }


        when:
        // Use strict conflict resolution
        buildFile.text = baseBuild + """
            configurations.conf.resolutionStrategy.failOnVersionConflict()
            dependencies {
                conf('org:bar:${dep1}')
                conf('org:bar') {
                    version { reject '${reject}' }
                }
            }
        """

        then:
        if (strictResult == "FAIL") {
            fails(":checkDeps")
        } else {
            succeeds(":checkDeps")
            resolve.expectGraph {
                root(":", ":test:") {
                    edge("org:bar:${dep1}", "org:bar:${strictResult}")
                    edge("org:bar", "org:bar:${strictResult}")
                }
            }
        }


        where:
        dep1         | reject       | lenientResult | strictResult | correctLenient | correctStrict
        "1.0"        | "[1.0, 1.4]" | "FAIL"        | "FAIL"       | ''             | ''
        "[1.0, 1.2]" | "1.0"        | "1.2"         | "1.2"        | ''             | ''
        "[1.0, 1.2]" | "[1.0, 1.1]" | "1.2"         | "1.2"        | ''             | ''
        "[1.0, 1.2]" | "[1.0, 1.4]" | "FAIL"        | "FAIL"       | ''             | ''
        "[1.0, 1.2]" | "[1.0, )"    | "FAIL"        | "FAIL"       | ''             | ''
        "[1.0, )"    | "1.0"        | "1.2"         | "1.2"        | ''             | ''
        "[1.0, )"    | "[1.0, 1.1]" | "1.2"         | "1.2"        | ''             | ''
        "[1.0, )"    | "[1.0, 1.4]" | "FAIL"        | "FAIL"       | ''             | ''
        "[1.0, )"    | "[1.0, )"    | "FAIL"        | "FAIL"       | ''             | ''
        "1.+"        | "1.0"        | "1.2"         | "1.2"        | ''             | ''
        "1.+"        | "[1.0, 1.1]" | "1.2"         | "1.2"        | ''             | ''
        "1.+"        | "[1.0, 1.4]" | "FAIL"        | "FAIL"       | ''             | ''
        "1.+"        | "[1.0, )"    | "FAIL"        | "FAIL"       | ''             | ''

        // Incorrect behaviour: should find older version in preferred range when newest is rejected
        "[1.0, 1.2]" | "1.2"        | "FAIL"        | "FAIL"       | "1.1"          | "1.1"
        "[1.0, )"    | "1.2"        | "FAIL"        | "FAIL"       | "1.1"          | "1.1"
        "1.+"        | "1.2"        | "FAIL"        | "FAIL"       | "1.1"          | "1.1"
    }


    @Unroll
    def "resolve #one & #two"() {
        expect:
        resolve(one, two) == lenientResult
        resolve(strict(one), two) == strict1Result
        resolve(one, strict(two)) == strict2Result
        resolve(strict(one), strict(two)) == strictBothResult

        resolve(two, one) == lenientResult
        resolve(two, strict(one)) == strict1Result
        resolve(strict(two), one) == strict2Result
        resolve(strict(two), strict(one)) == strictBothResult

        where:
        one         | two         | lenientResult | strict1Result | strict2Result | strictBothResult
        FIXED_7     | FIXED_13    | 13            | -1            | 13            | -1
        FIXED_12    | FIXED_13    | 13            | -1            | 13            | -1
        FIXED_12    | RANGE_10_11 | 12            | 12            | -1            | -1
        FIXED_12    | RANGE_10_14 | 12            | 12            | 12            | 12
        FIXED_12    | RANGE_13_14 | 13            | -1            | 13            | -1
        FIXED_12    | RANGE_7_8   | -1            | -1            | -1            | -1
        FIXED_12    | RANGE_14_16 | -1            | -1            | -1            | -1
        RANGE_10_11 | FIXED_10    | 10            | 10            | 10            | 10
        RANGE_10_14 | FIXED_13    | 13            | 13            | 13            | 13
        RANGE_10_14 | RANGE_10_11 | 11            | 11            | 11            | 11
        RANGE_10_14 | RANGE_10_16 | 13            | 13            | 13            | 13
    }

    @Unroll
    def "resolve #one & #two & #three"() {
        expect:
        [one, two, three].permutations().each {
            assert resolve(it[0], it[1], it[2]) == rst
        }
        [strict(one), two, three].permutations().each {
            assert resolve(it[0], it[1], it[2]) == strict1Result
        }
        [one, strict(two), three].permutations().each {
            assert resolve(it[0], it[1], it[2]) == strict2Result
        }
        [one, two, strict(three)].permutations().each {
            assert resolve(it[0], it[1], it[2]) == strict3Result
        }

        where:
        one      | two      | three    | rst | strict1Result | strict2Result | strict3Result
        FIXED_12 | FIXED_13 | FIXED_10 | 13  | -1            | 13            | -1
//        FIXED_10    | FIXED_12    | RANGE_10_14 | 12     | -1            | 12            | 12
//        FIXED_10    | RANGE_10_11 | RANGE_10_14 | 10     | 10            | 10            | 10

//        FIXED_10    | RANGE_11_12 | RANGE_10_14 | 12     | -1            | 12            | 12
        FIXED_10    | RANGE_10_11 | RANGE_13_14 | 13     | -1            | -1            | 13
//        RANGE_10_11 | RANGE_10_12 | RANGE_10_14 | 11     | 11            | 11            | 11
        RANGE_10_11 | RANGE_10_12 | RANGE_13_14 | 13     | -1            | -1            | 13
        RANGE_10_11 | RANGE_10_12 | RANGE_13_14 | 13     | -1            | -1            | 13

//         gradle/gradle#4608
//        FIXED_10    | FIXED_10    | FIXED_12    | 12     | -1            | -1            | 12

//        FIXED_12    | RANGE_12_14 | RANGE_10_11 | 12     | 12            | 12            | -1
//        FIXED_12    | RANGE_12_14 | FIXED_10    | 12     | 12            | 12            | -1
    }

    @Unroll
    def "resolve #one & #two & #three & #four"() {
        expect:
        [one, two, three, four].permutations().each {
            assert resolve(it[0], it[1], it[2], it[3]) == resolved
        }

        where:
        one     | two      | three    | four     | resolved
        FIXED_9 | FIXED_10 | FIXED_11 | FIXED_12 | 12
//        FIXED_10 | RANGE_10_11 | FIXED_12 | RANGE_12_14 | 12
        FIXED_10 | RANGE_10_11 | RANGE_10_12 | RANGE_13_14 | 13
//        FIXED_9  | RANGE_10_11 | RANGE_10_12 | RANGE_10_14 | 11
    }

    @Unroll
    def "resolve #dep and #reject"() {
        expect:
        resolve(dep, reject) == resolved
        resolve(reject, dep) == resolved
        resolve(strict(dep), reject) == strictResult
        resolve(reject, strict(dep)) == strictResult

        where:
        dep      | reject    | resolved | strictResult
        FIXED_12 | REJECT_11 | 12       | 12
        FIXED_12 | REJECT_12 | -1       | -1
        FIXED_12 | REJECT_13 | 12       | 12
    }

    @Unroll
    def "resolve #deps & reject 11/12/13"() {
        expect:
        deps.permutations().each {
            it
            assert resolve(it + REJECT_11) == reject11
            assert resolve(it + REJECT_12) == reject12
            assert resolve(it + REJECT_13) == reject13
            assert resolve([] + REJECT_11 + it) == reject11
            assert resolve([] + REJECT_12 + it) == reject12
            assert resolve([] + REJECT_13 + it) == reject13
        }

        where:
        deps                                              | reject11 | reject12 | reject13
        [FIXED_10, FIXED_11, FIXED_12]                    | 12       | -1       | 12
//        [RANGE_10_14, RANGE_10_12, FIXED_12]              | 12       | 13       | 12
//        [FIXED_10, RANGE_10_11, FIXED_12, RANGE_12_14]    | 12       | 13       | 12
        [FIXED_10, RANGE_10_11, RANGE_10_12, RANGE_13_14] | 13       | 13       | -1
//        [FIXED_9, RANGE_10_11, RANGE_10_12, RANGE_10_14]  | 10       | 11       | 11
    }

    private static RenderableVersion fixed(int version) {
        def vs = new SimpleVersion()
        vs.version = "${version}"
        return vs
    }

    private static RenderableVersion range(int low, int high) {
        def vs = new SimpleVersion()
        vs.version = "[${low},${high}]"
        return vs
    }

    private static RenderableVersion reject(int version) {
        def vs = new RejectVersion()
        vs.version = version
        vs
    }

    private static RenderableVersion strict(RenderableVersion input) {
        def v = new StrictVersion()
        v.version = input.version
        v
    }

    def resolve(RenderableVersion... versions) {
        resolve(versions as List)
    }

    def resolve(List<RenderableVersion> versions) {
        def deps = versions.collect {
            "conf " + it.render()
        }.join("\n")

        buildFile.text = baseBuild + """
            dependencies {
               $deps
            }
            task resolve(type: Sync) {
                from configurations.conf
                into 'libs'
            }
"""

        try {
            run 'resolve'
        } catch (Exception e) {
            return -1
        }

        def files = file('libs').listFiles()
        assert files.length == 1
        assert files[0].name.startsWith('foo-')
        assert files[0].name.endsWith('.jar')
        return (files[0].name =~ /\d\d/).getAt(0) as int
    }

    interface RenderableVersion {
        String getVersion()

        String render()
    }

    static class SimpleVersion implements RenderableVersion {
        String version

        @Override
        String render() {
            "'org:foo:${version}'"
        }

        @Override
        String toString() {
            return version
        }
    }

    static class StrictVersion implements RenderableVersion {
        String version

        @Override
        String render() {
            return "('org:foo') { version { strictly '${version}' } }"
        }

        @Override
        String toString() {
            return "strictly(" + version + ")"
        }
    }

    static class RejectVersion implements RenderableVersion {
        String version

        @Override
        String render() {
            "('org:foo') { version { reject '${version}' } }"
        }

        @Override
        String toString() {
            return "reject " + version
        }
    }
}
