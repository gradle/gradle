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
package org.gradle.launcher

import spock.lang.*
import org.gradle.messaging.remote.Address

class EmbeddedDaemonRegistrySpec extends Specification {

    @Delegate EmbeddedDaemonRegistry registry = new EmbeddedDaemonRegistry()

    def address() {
        [:] as Address
    }

    def storeEntry() {
        def entry = newEntry()
        entry.store(address())
        entry
    }
    
    def "initially empty"() {
        expect:
        all.empty
        idle.empty
        busy.empty
    }

    def "new entry does not make entry appear until store called"() {
        when:
        def entry = newEntry()
        
        then:
        all.empty
        
        when:
        entry.store(address())
        
        then:
        all.size() == 1
        idle.size() == 1
        busy.empty
    }

    def "lifecycle"() {
        given:
        def (e1, e2) = [storeEntry(), storeEntry()]
        
        expect:
        all.size() == 2
        idle.size() == 2
        busy.empty
        
        when:
        e1.markBusy()
        
        then:
        all.size() == 2
        idle.size() == 1
        busy.size() == 1
        
        when:
        e2.markBusy()
        
        then:
        all.size() == 2
        idle.empty
        busy.size() == 2
        
        when:
        e1.markIdle()
        e2.markIdle()
        
        then:
        all.size() == 2
        idle.size() == 2
        busy.empty
        
        when:
        e1.remove()
        e2.remove()
        
        then:
        all.empty
        idle.empty
        busy.empty
    }

}