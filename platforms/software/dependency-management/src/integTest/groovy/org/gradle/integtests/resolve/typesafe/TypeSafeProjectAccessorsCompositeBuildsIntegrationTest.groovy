/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.typesafe

import org.gradle.util.internal.ToBeImplemented

class TypeSafeProjectAccessorsCompositeBuildsIntegrationTest extends AbstractTypeSafeProjectAccessorsIntegrationTest {

    // not necessarily planned to be implemented, but capturing the existing behavior
    @ToBeImplemented
    def "included builds participate in type-safe accessors generation"() {
        settingsFile << """
            rootProject.name = 'test'

            includeBuild 'other'
        """
        file('other/settings.gradle') << """
            rootProject.name = 'other'
        """

        buildFile << """
            def projectDependency = projects.other
            assert projectDependency instanceof ProjectDependency
            println("Dependency path: \\"\${projectDependency.dependencyProject.path}\\"")
        """

        when:
        fails 'help'

        then:
        failureCauseContains("Could not get unknown property 'other' for extension 'projects' of type org.gradle.accessors.dm.RootProjectAccessor")
        // Desired behavior:
//        outputContains 'Dependency path: ":other"'
    }
}
