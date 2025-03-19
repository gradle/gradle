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

package org.gradle.internal.resource

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class ExternalResourceNameIntegrationTest extends AbstractIntegrationSpec {

    // Network shares on Linux and macOS are just normal file paths, so we don't have anything to test here
    @Requires(UnitTestPreconditions.Windows)
    def "can access repository on network share"() {
        //since we do not have a network share, we test the failure case and assert that the correct path was searched
        given:
        buildFile << """
        repositories {
            ivy { url = file("${hostPrefix}MISSING/folder/ivy") }
        }
        configurations { conf }
        dependencies {
            conf "org:name:1.0"
        }
        task resolve {
            def conf = configurations.conf
            doLast { conf.files.each {} }
        }
        """

        when:
        fails 'resolve'

        then:
        failure.assertHasCause """Could not find org:name:1.0.
Searched in the following locations:
  - file:////MISSING/folder/ivy/org/name/1.0/ivy-1.0.xml
"""
        where:
        // '//' - Normal UNC path for use with `new File(String)`
        // 'file:////' - UNC path encoded as a URI, for use with `new File(URI)`
        // No `file://` - that's using the "authority" instead, which `new File(URI)` doesn't support
        hostPrefix << ['//', 'file:////']
    }
}
