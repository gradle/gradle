/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.util.internal

import spock.lang.Specification

/**
 * by Szczepan Faber, created at: 3/2/12
 */
class GradleJvmSystemTest extends Specification {

    def existingSecurityManager = System.securityManager
        
    def cleanup() {
        System.securityManager = existingSecurityManager    
    }

    def "vm exit not permitted when gradle security is installed"() {
        given:
        GradleJvmSystem.installSecurity()

        when:
        System.exit(111)

        then:
        thrown(SecurityException)
    }

    def "gradle security manager can be overridden"() {
        given:
        GradleJvmSystem.installSecurity()

        when:
        System.securityManager = null

        then:
        System.securityManager == null
    }
    
    def "subsequent installation has no effect"() {
        given:
        GradleJvmSystem.installSecurity()
        def installed = System.securityManager
        
        when:
        GradleJvmSystem.installSecurity()
        
        then:
        System.securityManager.is(installed)
    }
}
