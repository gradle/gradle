/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.test.fixtures

import groovy.transform.CompileStatic
import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

@CompileStatic
@SelfType(AbstractIntegrationSpec)
trait DependencyDeclarationFixture {

    /**
     * Escapes the input so that it can be used in a single-quoted string.
     */
    String sq(String input) {
        return input.replace('\\', '\\\\').replace('\'', '\\\'')
    }

    /**
     * Converts the given group, name, and version into code that may be used in
     * a Groovy buildscript as a dependency notation.
     */
    String asDependencyNotation(String group, String name, String version) {
        group = sq(group)
        name = sq(name)
        version = sq(version)

        def hasDelimiterInCoordinates = [group, name, version].any { it.contains(':') || it.contains('@') }

        // Most of the time, we want to test with the single-string notation, as this is the
        // normal way users will be doing so in their build scripts.
        if (!hasDelimiterInCoordinates) {
            return "'${group}:${name}:${version}'"
        }

        // Only in special cases, when coordinates are using unusual characters, do we
        // use dependency factory to create a notation.
        return "project.dependencyFactory.create('${group}', '${name}', '${version}')"
    }

}
