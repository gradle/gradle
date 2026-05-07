/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.authentication

import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.authentication.Authentication
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

class DefaultAuthenticationContainerTest extends Specification {
    @Subject
    def container = new DefaultAuthenticationContainer(TestUtil.instantiatorFactory().decorateLenient(), CollectionCallbackActionDecorator.NOOP)

    def setup() {
        container.registerBinding(TestAuthentication, DefaultTestAuthentication)
        container.registerBinding(CustomTestAuthentication, DefaultCustomTestAuthentication)
    }

    def "can add multiple authentication schemes with common supertype"() {
        when:
        container.create('auth1', auth1)
        container.create('auth2', auth2)

        then:
        container.size() == 2

        where:
        auth1                    | auth2
        TestAuthentication       | CustomTestAuthentication
        CustomTestAuthentication | TestAuthentication
    }

    static interface TestAuthentication extends Authentication {}

    static interface CustomTestAuthentication extends TestAuthentication {}

    static class DefaultTestAuthentication extends AbstractAuthentication implements TestAuthentication {
        DefaultTestAuthentication(String name) {
            super(name, TestAuthentication)
        }
        DefaultTestAuthentication(String name, Class type) {
            super(name, type)
        }

        public boolean requiresCredentials() {
            return true;
        }
    }

    static class DefaultCustomTestAuthentication extends DefaultTestAuthentication implements CustomTestAuthentication {
        DefaultCustomTestAuthentication(String name) {
            super(name, CustomTestAuthentication)
        }
    }
}
