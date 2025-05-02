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
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.maven.MavenModule

@CompileStatic
@SelfType(AbstractIntegrationSpec)
trait GradleMetadataAwarePublishingSpec {
    boolean requiresExternalDependencies

    static String sq(String input) {
        return escapeForSingleQuoting(input)
    }

    static String escapeForSingleQuoting(String input) {
        return input.replace('\\', '\\\\').replace('\'', '\\\'')
    }

    static String convertDependencyNotation(Object notation) {
        if (notation instanceof CharSequence) {
            return notation
        }
        if (notation instanceof IvyModule) {
            return "group: '${sq(notation.organisation)}', name: '${sq(notation.module)}', version: '${sq(notation.revision)}'"
        }
        if (notation instanceof MavenModule) {
            return "group: '${sq(notation.groupId)}', name: '${sq(notation.artifactId)}', version: '${sq(notation.version)}'"
        }
        throw new UnsupportedOperationException("Unsupported dependency notation: $notation")
    }

}
