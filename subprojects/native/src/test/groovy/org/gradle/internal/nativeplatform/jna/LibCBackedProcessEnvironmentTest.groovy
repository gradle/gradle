/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeplatform.jna

import org.gradle.internal.nativeplatform.NativeIntegrationException
import org.gradle.internal.nativeplatform.services.NativeServices
import spock.lang.Specification
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

class LibCBackedProcessEnvironmentTest extends Specification {

    NativeServices registry = NativeServices.getInstance();

    @Requires(TestPrecondition.FILE_PERMISSIONS) //MACOSX & UNIX
    def "setNativeEnvironmentVariable throws NativeEnvironmentException for NULL value on env name with errno code"() {
        setup:
        LibCBackedProcessEnvironment processEnvironment = new LibCBackedProcessEnvironment(registry.get(LibC.class))
        when:
        processEnvironment.setNativeEnvironmentVariable(null, "TEST_ENV_VAR");
        then:
        def e = thrown(NativeIntegrationException);
        e.message == "Could not set environment variable 'null'. errno: 22"
    }
}
