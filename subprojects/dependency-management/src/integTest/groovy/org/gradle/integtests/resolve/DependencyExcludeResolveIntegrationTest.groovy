/*
 * Copyright 2015 the original author or authors.
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
import spock.lang.Unroll

class DependencyExcludeResolveIntegrationTest extends AbstractDependencyResolutionTest {
    /**
     * Dependency exclude rules defined through Gradle DSL.
     *
     * Dependency graph:
     *
     * org.gradle:test:1.45 -> org.gradle:foo:2.0, org.gradle:bar:3.0, com.company:baz:4.0
     * com.company:baz:4.0 -> com.enterprise:some:5.0
     */
    @Unroll
    def "dependency exclude rule for #condition"() {
        given:
        final String orgGradleGroupId = 'org.gradle'
        def testModule = mavenRepo().module(orgGradleGroupId, 'test', '1.45')
        def fooModule = mavenRepo().module(orgGradleGroupId, 'foo', '2.0')
        fooModule.publish()
        def barModule = mavenRepo().module(orgGradleGroupId, 'bar', '3.0')
        barModule.publish()

        def bazModule = mavenRepo().module('com.company', 'baz', '4.0')
        def someModule = mavenRepo().module('com.enterprise', 'some', '5.0')
        bazModule.dependsOn(someModule.groupId, someModule.artifactId, someModule.version)
        bazModule.publish()
        someModule.publish()

        testModule.dependsOn(fooModule.groupId, fooModule.artifactId, fooModule.version)
        testModule.dependsOn(barModule.groupId, barModule.artifactId, barModule.version)
        testModule.dependsOn(bazModule.groupId, bazModule.artifactId, bazModule.version)
        testModule.publish()

        and:
        buildFile << """
repositories { maven { url "${mavenRepo().uri}" } }
configurations { compile }
dependencies {
    compile('${testModule.groupId}:${testModule.artifactId}:${testModule.version}') {
        exclude ${excludeAttributes.collect { key, value -> "$key: '$value'"}.join(', ')}
    }
}

task check << {
    assert configurations.compile.collect { it.name } == [${resolvedJars.collect { "'$it'" }.join(", ")}]
}
"""

        expect:
        succeeds "check"

        where:
        condition                                                                     | excludeAttributes                        | resolvedJars
        'no matching group attribute'                                                 | [group: 'some.other']                    | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'baz-4.0.jar', 'some-5.0.jar']
        'matching group attribute same as declared module'                            | [group: 'org.gradle']                    | ['test-1.45.jar', 'baz-4.0.jar', 'some-5.0.jar']
        'matching group attribute other than declared module'                         | [group: 'com.company']                   | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar']
        'no matching group and module attributes'                                     | [group: 'some.other', module: 'unknown'] | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'baz-4.0.jar', 'some-5.0.jar']
        'matching group attribute same as declared module but other module attribute' | [group: 'org.gradle', module: 'foo']     | ['test-1.45.jar', 'bar-3.0.jar', 'baz-4.0.jar', 'some-5.0.jar']
        'matching group and module attributes other than declared module'             | [group: 'com.company', module: 'baz']    | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar']
        'same group and module attributes as declared module'                         | [group: 'org.gradle', module: 'test']    | ['test-1.45.jar', 'foo-2.0.jar', 'bar-3.0.jar', 'baz-4.0.jar', 'some-5.0.jar']
    }
}
