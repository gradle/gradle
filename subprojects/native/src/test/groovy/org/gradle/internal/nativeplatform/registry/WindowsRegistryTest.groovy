package org.gradle.internal.nativeplatform.registry

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Specification

@Requires(TestPrecondition.WINDOWS)
class WindowsRegistryTest extends Specification {
    final String VERSION_SUBKEY = "SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion";
    final String VERSION_NAME = "CurrentVersion";
    final String ENVIRONMENT_SUBKEY = "Volatile Environment";
    final String ENVIRONMENT_PATH = "USERNAME";

    def "local machine registry can be accessed"() {
        expect:
        WindowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE).readString(VERSION_SUBKEY, VERSION_NAME) != null;
    }

    def "current user registry can be accessed"() {
        expect:
        WindowsRegistry.get(WindowsRegistry.HKEY_CURRENT_USER).readString(ENVIRONMENT_SUBKEY, ENVIRONMENT_PATH) != null;
    }

    def "accessing a subkey that does not exist throws an exception"() {
        when:
        WindowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE).readString("inexistent subkey", VERSION_NAME);

        then:
        thrown(WindowsRegistryException)
    }
    
    def "accessing a value that does not exist throws an exception"() {
        when:
        WindowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE).readString(VERSION_SUBKEY, "inexistent name");

        then:
        thrown(WindowsRegistryException)
    }

}
