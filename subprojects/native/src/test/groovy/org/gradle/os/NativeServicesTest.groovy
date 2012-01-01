/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.os

import spock.lang.Specification
import org.jruby.ext.posix.POSIX

class NativeServicesTest extends Specification {
    final NativeServices services = new NativeServices()
    
    def "makes a ProcessEnvironment available"() {
        expect:
        services.get(ProcessEnvironment) != null
    }

    def "makes an OperatingSystem available"() {
        expect:
        services.get(OperatingSystem) != null
    }

    def "makes a POSIX available"() {
        expect:
        services.get(POSIX) != null
    }

    def "fails for unknown type"() {
        when:
        services.get(String)

        then:
        IllegalArgumentException e = thrown()
        assert e.message == 'Do not know how to create service of type String.'
    }
}
