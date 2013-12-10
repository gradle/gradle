/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.nativeplatform.registry

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification

@Requires(TestPrecondition.WINDOWS)
class WindowsRegistryTest extends Specification {
    static final String SOFTWARE_SUBKEY = "SOFTWARE";
    static final String VERSION_SUBKEY = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";
    static final String VERSION_NAME = "CurrentVersion";
    static final String ENVIRONMENT_SUBKEY = "Volatile Environment";
    static final String ENVIRONMENT_PATH = "USERNAME";
    final WindowsRegistry windowsRegistry = new WindowsRegistry()

    def "local machine registry can be accessed"() {
        expect:
        windowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE).readString(VERSION_SUBKEY, VERSION_NAME) != null;
    }

    def "current user registry can be accessed"() {
        expect:
        windowsRegistry.get(WindowsRegistry.HKEY_CURRENT_USER).readString(ENVIRONMENT_SUBKEY, ENVIRONMENT_PATH) != null;
    }

    def "accessing a subkey that does not exist throws an exception"() {
        when:
        windowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE).readString("inexistent subkey", VERSION_NAME);

        then:
        thrown(WindowsRegistryException)
    }
    
    def "accessing a value that does not exist throws an exception"() {
        when:
        WindowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE).readString(VERSION_SUBKEY, "inexistent name");

        then:
        thrown(WindowsRegistryException)
    }

    def "subkeys can be enumerated"() {
        expect:
        !windowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE).getSubkeys(SOFTWARE_SUBKEY).isEmpty()
    }

}
