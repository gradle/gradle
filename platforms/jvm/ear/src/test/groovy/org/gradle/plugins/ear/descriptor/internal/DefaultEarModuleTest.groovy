/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ear.descriptor.internal

import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultEarModuleTest extends Specification {

    def "equals works correctly"(lhs, rhs, equals) {

        expect:
        lhs.equals(rhs) == equals
        rhs.equals(lhs) == equals

        where:
        lhs || rhs || equals
        newDefaultEarModule("some.jar") | newDefaultEarModule("some-other.jar") | false
        newDefaultEarModule("some.jar") | newDefaultEarModule("some.jar") | true
        newDefaultEarModule("some.jar", "some.xml") | newDefaultEarModule("some.jar") | false
        newDefaultEarSecurityRole("role", "description") | newDefaultEarSecurityRole("role", "description") | true
        newDefaultEarSecurityRole("role") | newDefaultEarSecurityRole("role") | true
        newDefaultEarSecurityRole("role") | newDefaultEarSecurityRole("other-role") | false
        newDefaultEarSecurityRole("role", "description") | newDefaultEarSecurityRole("other-role", "other-description") | false
    }

    private static DefaultEarModule newDefaultEarModule(String path, String altDeployDescriptor = null) {
        return TestUtil.objectFactory().newInstance(DefaultEarModule).tap {
            it.path = path
            it.altDeployDescriptor = altDeployDescriptor
        }
    }

    private static DefaultEarSecurityRole newDefaultEarSecurityRole(String roleName, String description = null) {
        return TestUtil.objectFactory().newInstance(DefaultEarSecurityRole).tap {
            it.roleName = roleName
            it.description = description
        }
    }
}
