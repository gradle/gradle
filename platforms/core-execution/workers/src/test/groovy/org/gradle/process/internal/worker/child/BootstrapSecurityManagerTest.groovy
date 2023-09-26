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

package org.gradle.process.internal.worker.child

import org.gradle.integtests.fixtures.RedirectStdIn
import org.gradle.internal.stream.EncodedStream
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

import java.security.AllPermission
import java.security.Permission

@RedirectStdIn
@Requires(UnitTestPreconditions.Jdk8OrEarlier)
class BootstrapSecurityManagerTest extends Specification {
    @Rule SetSystemProperties systemProperties

    def cleanup() {
        System.securityManager = null
    }

    def "reads classpath from System.in and sets up system classpath on first permission check"() {
        def entry1 = new File("a.jar")
        def entry2 = new File("b.jar")
        TestClassLoader cl = Mock()

        given:
        System.in = createStdInContent(entry1, entry2)

        when:
        def securityManager = new BootstrapSecurityManager(cl)

        then:
        0 * cl._

        when:
        securityManager.checkPermission(new AllPermission())

        then:
        1 * cl.addURL(entry1.toURI().toURL())
        1 * cl.addURL(entry2.toURI().toURL())
        0 * cl._
        System.getProperty("java.class.path") == [entry1.absolutePath, entry2.absolutePath].join(File.pathSeparator)

        when:
        securityManager.checkPermission(new AllPermission())

        then:
        0 * cl._
        System.getProperty("java.class.path") == [entry1.absolutePath, entry2.absolutePath].join(File.pathSeparator)
    }

    def "fails with proper error message if System.in is not delivering all expected data"() {
        given:
        def incompleteStream = new ByteArrayOutputStream()
        def dataOut = new DataOutputStream(new EncodedStream.EncodedOutput(incompleteStream))
        dataOut.writeInt(1) // expect one classpath entry
        dataOut.write(1234) // but the entry is not a complete UTF-8 encoded String

        System.in = new ByteArrayInputStream(incompleteStream.toByteArray())

        when:
        new BootstrapSecurityManager(new TestClassLoader()).checkPermission(new AllPermission())

        then:
        RuntimeException e = thrown()
        e.message == "Could not initialise system classpath."
        e.cause instanceof EOFException
    }

    def "installs custom SecurityManager"() {
        URLClassLoader cl = new URLClassLoader([] as URL[], getClass().classLoader)

        given:
        System.in = createStdInContent(TestSecurityManager.class)

        when:
        def securityManager = new BootstrapSecurityManager(cl)
        securityManager.checkPermission(new AllPermission())

        then:
        System.securityManager instanceof TestSecurityManager
    }

    def createStdInContent(Class securityManager = null, File... classpath) {
        def out = new ByteArrayOutputStream()
        def dataOut = new DataOutputStream(new EncodedStream.EncodedOutput(out))
        dataOut.writeInt(classpath.length)
        classpath.each { dataOut.writeUTF(it.absolutePath) }
        dataOut.writeUTF(securityManager ? securityManager.name : "")
        return new ByteArrayInputStream(out.toByteArray())
    }

    static class TestClassLoader extends URLClassLoader {
        TestClassLoader(URL[] urls) {
            super(urls)
        }

        @Override
        void addURL(URL url) {
        }
    }

    static class TestSecurityManager extends SecurityManager {
        @Override
        void checkPermission(Permission permission) {
        }
    }
}
