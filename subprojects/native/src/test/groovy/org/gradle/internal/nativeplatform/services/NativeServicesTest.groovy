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
package org.gradle.internal.nativeplatform.services

import org.gradle.internal.nativeplatform.ProcessEnvironment
import org.gradle.internal.nativeplatform.ConsoleDetector
import org.gradle.internal.nativeplatform.filesystem.FileSystem
import org.gradle.internal.os.OperatingSystem
import spock.lang.Specification
import org.gradle.internal.console.ConsoleMetaData

class NativeServicesTest extends Specification {
    final NativeServices services = NativeServices.getInstance()
    
    def "makes a ProcessEnvironment available"() {
        expect:
        services.get(ProcessEnvironment) != null
    }

    def "makes an OperatingSystem available"() {
        expect:
        services.get(OperatingSystem) != null
    }

    def "makes a FileSystem available"() {
        expect:
        services.get(FileSystem) != null
    }

    def "makes a TerminalDetector available"() {
        expect:
        services.get(ConsoleDetector) != null
    }

    def "makes ConsoleMetaData available"() {
        expect:
        services.get(ConsoleMetaData) != null
    }
}
