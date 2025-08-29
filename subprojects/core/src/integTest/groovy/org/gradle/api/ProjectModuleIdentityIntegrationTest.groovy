/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Tests project behavior related to module identity:
 * <ul>
 *     <li>{@link Project#getGroup()}</li>
 *     <li>{@link Project#getVersion()}</li>
 * <ul>
 */
class ProjectModuleIdentityIntegrationTest extends AbstractIntegrationSpec {

    def "default group for root project is an empty string"() {
        buildFile("""
            assert group == ""
        """)

        expect:
        succeeds("help")
    }

    def "default group for subproject is based on project structure"() {
        given:
        settingsFile("""
            rootProject.name = "root"
            include(":sub")
            include(":sub:subsub")
        """)

        buildFile("sub/build.gradle", """
            assert group == "root"
        """)
        buildFile("sub/subsub/build.gradle", """
            assert group == "root.sub"
        """)

        expect:
        succeeds("help")
    }

    def "default version for project is 'unspecified'"() {
        settingsFile("""
            rootProject.name = "root"
            include(":sub")
        """)
        buildFile("""
            assert version == "unspecified"
        """)
        buildFile("sub/build.gradle", """
            assert version == "unspecified"
        """)

        expect:
        succeeds("help")
    }

}
