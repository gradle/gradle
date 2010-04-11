/*
 * Copyright 2010 the original author or authors.
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



package org.gradle.api.testing.execution.fork

import spock.lang.Specification
import java.security.Policy
import java.security.ProtectionDomain
import java.security.Permission

class TestPolicyTest extends Specification {
    private final Policy target = Mock()
    private final ClassLoader systemClassLoader = TestPolicy.class.getClassLoader()
    private final Permission permission = Mock()
    private final TestPolicy policy = new TestPolicy(target)

    def grantsAllPermissionsToCodeFromSystemClassLoader() {
        ProtectionDomain protectionDomain = new ProtectionDomain(null, null, systemClassLoader, null)

        expect:
        policy.implies(protectionDomain, permission)
    }
    
    def delegatesAccessControlCheckToBackingPolicy() {
        ProtectionDomain protectionDomain = new ProtectionDomain(null, null, Mock(ClassLoader), null)

        when:
        policy.implies(protectionDomain, permission)

        then:
        1 * target.implies(protectionDomain, permission)
    }

    def delegatesReloadToBackingPolicy() {
        when:
        policy.refresh()

        then:
        1 * target.refresh()
    }
    
}
