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
package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.internal.artifacts.IvyService
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact

import spock.lang.*

class DefaultConfigurationSpec extends Specification {

    ConfigurationsProvider configurationsProvider = [:] as ConfigurationsProvider
    IvyService ivyService = [:] as IvyService

    DefaultConfiguration conf(String confName = "conf") {
        new DefaultConfiguration(confName, confName, configurationsProvider, ivyService)
    }
    
    DefaultPublishArtifact artifact(String name) {
        artifact(name: name)
    }
    
    DefaultPublishArtifact artifact(Map props = [:]) {
        new DefaultPublishArtifact(
            props.name ?: "artifact",
            props.extension ?: "artifact",
            props.type,
            props.classifier,
            props.date,
            props.file,
            props.tasks ?: []
        )
    }

    def "all artifacts collection has immediate artifacts"() {
        given:
        def c = conf()
        
        when:
        c.addArtifact(artifact())
        c.addArtifact(artifact())
        
        then:
        c.allArtifactsCollection.all.size() == 2
    }

    def "all artifacts collection has inherited artifacts"() {
        given:
        def master = conf()
        
        def masterParent1 = conf()
        def masterParent2 = conf()
        master.extendsFrom masterParent1, masterParent2
        
        def masterParent1Parent1 = conf()
        def masterParent1Parent2 = conf()
        masterParent1.extendsFrom masterParent1Parent1, masterParent1Parent2
        
        def masterParent2Parent1 = conf()
        def masterParent2Parent2 = conf()
        masterParent2.extendsFrom masterParent2Parent1, masterParent2Parent2
        
        def allArtifacts = master.allArtifactsCollection
        
        def added = []
        allArtifacts.whenObjectAdded { added << it.name }
        def removed = []
        allArtifacts.whenObjectRemoved { removed << it.name }
        
        expect:
        allArtifacts.all.empty
        
        when:
        masterParent1.addArtifact(artifact("p1-1"))
        masterParent1Parent1.addArtifact(artifact("p1p1-1"))
        masterParent1Parent2.addArtifact(artifact("p1p2-1"))
        masterParent2.addArtifact(artifact("p2-1"))
        masterParent2Parent1.addArtifact(artifact("p2p1-1"))
        masterParent2Parent2.addArtifact(artifact("p2p2-1"))
        
        then:
        allArtifacts.all.size() == 6
        added == ["p1-1", "p1p1-1", "p1p2-1", "p2-1", "p2p1-1", "p2p2-1"]
        
        when:
        masterParent2Parent2.removeArtifact masterParent2Parent2.artifacts.toList().first()
        
        then:
        allArtifacts.all.size() == 5
        removed == ["p2p2-1"]
        
        when:
        removed.clear()
        masterParent1.extendsFrom = []
        
        then:
        allArtifacts.all.size() == 3
        removed == ["p1p1-1", "p1p2-1"]
    }
    
}