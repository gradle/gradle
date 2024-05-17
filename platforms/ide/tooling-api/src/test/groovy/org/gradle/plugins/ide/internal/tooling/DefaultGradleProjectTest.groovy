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
package org.gradle.plugins.ide.internal.tooling

import org.gradle.plugins.ide.internal.tooling.model.DefaultGradleProject
import org.gradle.tooling.internal.gradle.DefaultProjectIdentifier
import spock.lang.Specification

class DefaultGradleProjectTest extends Specification {

    def "allows finding descendant by path"() {
        def root = new DefaultGradleProject().setProjectIdentifier(new DefaultProjectIdentifier(new File("."), ":"))

        def child1 = new DefaultGradleProject().setProjectIdentifier(new DefaultProjectIdentifier(new File("."), ":child1"))
        def child11 = new DefaultGradleProject().setProjectIdentifier(new DefaultProjectIdentifier(new File("."), ":child1:child11"))
        def child12 = new DefaultGradleProject().setProjectIdentifier(new DefaultProjectIdentifier(new File("."), ":child1:child12"))

        def child2 = new DefaultGradleProject().setProjectIdentifier(new DefaultProjectIdentifier(new File("."), ":child2"))
        def child21 = new DefaultGradleProject().setProjectIdentifier(new DefaultProjectIdentifier(new File("."), ":child2:child21"))

        root.children = [child1, child2]
        child1.children = [child11, child12]
        child2.children = [child21]

        expect:
        root.findByPath(':') == root
        root.findByPath('') == null
        root.findByPath('blah blah') == null

        root.findByPath(':child1:child12') == child12
        root.findByPath(':child2') == child2

        child1.findByPath(':') == null
        child1.findByPath(':child1:child11') == child11
    }
}
