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

package org.gradle.api.internal.artifacts.result

import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId
import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.util.Matchers.strictlyEquals

/**
 * by Szczepan Faber, created at: 8/29/12
 */
class DefaultUnresolvedDependencyResultSpec extends Specification {

    def "object methods"() {
        def dependency = new DefaultUnresolvedDependencyResult(newSelector("a", "b", "1"), new RuntimeException("Boo!"), new DefaultResolvedModuleVersionResult(newId("a", "root", "1")))
        def same = new DefaultUnresolvedDependencyResult(newSelector("a", "b", "1"), new RuntimeException("Boo!"), new DefaultResolvedModuleVersionResult(newId("a", "root", "1")))

        def differentRequested = new DefaultUnresolvedDependencyResult(newSelector("a", "b", "5.0"), new RuntimeException("Boo!"), new DefaultResolvedModuleVersionResult(newId("a", "root", "1")))
        def differentException = new DefaultUnresolvedDependencyResult(newSelector("a", "b", "1"), new RuntimeException("Yo!"), new DefaultResolvedModuleVersionResult(newId("a", "root", "1")))
        def differentFrom = new DefaultUnresolvedDependencyResult(newSelector("a", "b", "1"), new RuntimeException("Boo!"), new DefaultResolvedModuleVersionResult(newId("a", "root", "5.0")))

        expect:
        strictlyEquals(dependency, same)
        strictlyEquals(dependency, differentException)

        dependency != differentRequested
        dependency != differentFrom

        dependency.hashCode() != differentRequested.hashCode()
        dependency.hashCode() != differentFrom.hashCode()
    }
}