/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.internal.initialization.ClassLoaderScopeIdentifier
import org.gradle.initialization.ClassLoaderScopeRegistryListenerManager
import org.gradle.internal.classpath.DefaultClassPath
import spock.lang.Specification

class ConfigurationCacheClassLoaderScopeRegistryListenerTest extends Specification {

    def listenerManager = Mock(ClassLoaderScopeRegistryListenerManager)
    def listener = new ConfigurationCacheClassLoaderScopeRegistryListener(listenerManager)

    def rootId = new ClassLoaderScopeIdentifier(null, "root")
    def scopeId = rootId.child("scope")

    def "registers when the build tree starts and unregisters when recording stops"() {
        when:
        listener.afterBuildTreeStart()

        then:
        1 * listenerManager.add(listener)

        when:
        listener.stopRecording()

        then:
        1 * listenerManager.remove(listener)
    }

    def "records the scope of a class loader"() {
        given:
        listener.afterBuildTreeStart()
        def classLoader = classLoader()

        when:
        listener.childScopeCreated(rootId, scopeId, null)
        listener.classloaderCreated(scopeId, scopeId.localId(), classLoader, DefaultClassPath.of(), null)

        then:
        def scopeAndRole = listener.scopeFor(classLoader)
        scopeAndRole.first.name == "scope"
        scopeAndRole.second.local
    }

    def "rejects a second start, so that recorded scopes cannot be discarded"() {
        given:
        listener.afterBuildTreeStart()

        when:
        listener.afterBuildTreeStart()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot start recording ClassLoaderScopes in state ACTIVE."
    }

    def "rejects stopping a recording that never started"() {
        when:
        listener.stopRecording()

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("Cannot stop recording ClassLoaderScopes in state IDLE.")
    }

    def "cannot look up a scope once recording stopped"() {
        given:
        listener.afterBuildTreeStart()
        def classLoader = classLoader()
        listener.childScopeCreated(rootId, scopeId, null)
        listener.classloaderCreated(scopeId, scopeId.localId(), classLoader, DefaultClassPath.of(), null)
        listener.stopRecording()

        when:
        listener.scopeFor(classLoader)

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("Cannot look up a ClassLoaderScope in state DISPOSED.")
    }

    def "stopping an already stopped recording does nothing"() {
        given:
        listener.afterBuildTreeStart()
        listener.stopRecording()

        when:
        listener.stopRecording()

        then:
        noExceptionThrown()
        0 * listenerManager.remove(_)
    }

    def "rejects a scope for a class loader already reported as having none"() {
        given:
        listener.afterBuildTreeStart()
        def classLoader = classLoader()
        listener.childScopeCreated(rootId, scopeId, null)

        and:
        assert listener.scopeFor(classLoader) == null

        when:
        listener.classloaderCreated(scopeId, scopeId.localId(), classLoader, DefaultClassPath.of(), null)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains("after that loader was reported as having no scope")
    }

    def "rejects an event once recording stopped"() {
        given:
        listener.afterBuildTreeStart()
        listener.stopRecording()

        when:
        listener.childScopeCreated(rootId, scopeId, null)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Received a ClassLoaderScope event for $scopeId in state DISPOSED."
    }

    def "can be closed before the build tree started"() {
        when:
        listener.close()

        then:
        noExceptionThrown()
        0 * listenerManager.remove(_)
    }

    def "closing while recording unregisters the listener"() {
        given:
        listener.afterBuildTreeStart()

        when:
        listener.close()

        then:
        1 * listenerManager.remove(listener)
    }

    def "can be closed after the recording stopped"() {
        given:
        listener.afterBuildTreeStart()
        listener.stopRecording()

        when:
        listener.close()

        then:
        noExceptionThrown()
        0 * listenerManager.remove(_)
    }

    private static ClassLoader classLoader() {
        new URLClassLoader(new URL[0], null)
    }
}
