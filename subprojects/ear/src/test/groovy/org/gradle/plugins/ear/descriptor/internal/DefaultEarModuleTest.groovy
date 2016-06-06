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

import spock.lang.Specification

class DefaultEarModuleTest extends Specification {

    def "equals works correctly"(lhs, rhs, equals) {

        expect:
        lhs.equals(rhs) == equals
        rhs.equals(lhs) == equals

        where:
        lhs || rhs || equals
        new DefaultEarModule("some.jar") | new DefaultEarModule("some-other.jar") | false
        new DefaultEarModule("some.jar") | new DefaultEarModule("some.jar") | true
        new DefaultEarModule(path: "some.jar", altDeployDescriptor: "some.xml") | new DefaultEarModule("some.jar") | false
        new DefaultEarSecurityRole("role", "description") | new DefaultEarSecurityRole("role", "description") | true
        new DefaultEarSecurityRole("role") | new DefaultEarSecurityRole("role") | true
        new DefaultEarSecurityRole("role") | new DefaultEarSecurityRole("other-role") | false
        new DefaultEarSecurityRole("role", "description") | new DefaultEarSecurityRole("other-role", "other-description") | false
    }
}
