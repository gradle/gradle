/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests;

import org.gradle.integtests.fixtures.AbstractIntegrationSpec;

public class MultiProjectDependencyIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << 'include "a", "b", "c", "d"'
        buildFile << """
allprojects {
    apply plugin: 'java'

    task copyLibs(type: Copy) {
        from configurations.compile
        into 'deps'
    }

    compileJava.dependsOn copyLibs
}
"""
    }

    def "will honour :c->[:a,:b]"() {
        projectDependency from: ':c', to: [':a', ':b']
        when:
        run ':c:build'

        then:
        jarsBuilt 'a', 'b', 'c'
        depsCopied 'a', []
        depsCopied 'b', []
        depsCopied 'c', ['a', 'b']
    }

    def "will honour :c->:b->:a"() {

        projectDependency from: ':c', to: [':b']
        projectDependency from: ':b', to: [':a']

        when:
        run ':c:build'

        then:
        jarsBuilt 'a', 'b', 'c'
        depsCopied 'c', ['a', 'b']
        depsCopied 'b', ['a']
        depsCopied 'a', []
    }

    def "will honour :a->[:b,:c] where :b->:c"() {

        projectDependency from: ':a', to: [':b', ':c']
        projectDependency from: ':b', to: [':c']

        when:
        run ':a:build'

        then:
        jarsBuilt 'a', 'b', 'c'
        depsCopied 'a', ['b', 'c']
        depsCopied 'b', ['c']
        depsCopied 'c', []
    }

    def "will honour :a->[:b,:d] where :b->:c->:d"() {

        projectDependency from: ':a', to: [':b', ':d']
        projectDependency from: ':b', to: [':c']
        projectDependency from: ':c', to: [':d']

        when:
        run ':a:build'

        then:
        jarsBuilt 'a', 'b', 'c', 'd'
        depsCopied 'a', ['b', 'c', 'd']
        depsCopied 'b', ['c', 'd']
        depsCopied 'c', ['d']
        depsCopied 'd', []
    }

    def projectDependency(def link) {
        def from = link['from']
        def to = link['to']

        def dependencies = to.collect {
            "compile project('${it}')"
        }.join('\n')

        buildFile << """
project('$from') {
    dependencies {
        ${dependencies}
    }
}
"""
    }

    def jarsBuilt(String... projects) {
        projects.each {
            file("${it}/build/libs/${it}.jar").assertExists()
        }
    }

    def depsCopied(String project, Collection<String> deps) {
        def depsDir = file("${project}/deps")
        if (deps.isEmpty()) {
            depsDir.assertDoesNotExist()
        } else {
            String[] depJars = deps.collect {
                "${it}.jar"
            }
            depsDir.assertHasDescendants(depJars)
        }
    }
}
