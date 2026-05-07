/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.util.Path
import spock.lang.Specification

class ProjectIdentityTest extends Specification {

    // This display name format is what Project.toString() and Project.getDisplayName() produce.
    // Changing it affects user-visible output in logs, error messages, and dependency reports.

    def "root project of root build has display name with project name"() {
        def identity = ProjectIdentity.forRootProject(Path.ROOT, "myApp")

        expect:
        identity.displayName == "root project 'myApp'"
        identity.toString() == "root project 'myApp'"
        identity.capitalizedDisplayName == "Root project 'myApp'"
    }

    def "subproject of root build has display name with identity path"() {
        def identity = ProjectIdentity.forSubproject(Path.ROOT, Path.path(":sub"))

        expect:
        identity.displayName == "project ':sub'"
        identity.toString() == "project ':sub'"
        identity.capitalizedDisplayName == "Project ':sub'"
    }

    def "root project of included build has display name with build tree path"() {
        def identity = ProjectIdentity.forRootProject(Path.path(":included"), "includedApp")

        expect:
        identity.displayName == "project ':included'"
        identity.toString() == "project ':included'"
    }

    def "subproject of included build has display name with full identity path"() {
        def identity = ProjectIdentity.forSubproject(Path.path(":included"), Path.path(":sub"))

        expect:
        identity.displayName == "project ':included:sub'"
        identity.toString() == "project ':included:sub'"
    }
}
