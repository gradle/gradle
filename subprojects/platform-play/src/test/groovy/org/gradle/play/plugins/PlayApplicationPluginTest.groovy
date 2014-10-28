/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins

import org.gradle.api.artifacts.ResolveException
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.TestUtil
import spock.lang.Specification

class PlayApplicationPluginTest extends Specification {

    DefaultProject project = TestUtil.createRootProject()

    def setup(){
        project.getPluginManager().apply(PlayApplicationPlugin.class);
    }

    def "adds twirl configuration with meaningful description"(){
        expect:
        project.configurations.twirl != null
        project.configurations.twirl.description == "The dependencies to be used Play Twirl template compilation."
    }

    def "declares twirl default dependency"(){
        expect:
        project.configurations.twirl.getDependencies().isEmpty()

        when:
        project.configurations.twirl.files
        then:
        def e = thrown(ResolveException)
        e.cause.message.contains("Cannot resolve external dependency ${PlayApplicationPlugin.DEFAULT_TWIRL_DEPENDENCY}")
    }

    def "can overwrite twirl default dependency"(){
        when:
        project.dependencies{
            twirl "some-non:default-twirl:1.2.3"
        }
        then:
        project.configurations.twirl.getDependencies().collect {"${it.group}:${it.name}:${it.version}"} == ["some-non:default-twirl:1.2.3"]

        when:
        project.configurations.twirl.files
        then:
        def e = thrown(ResolveException)
        e.cause.message.contains("Cannot resolve external dependency some-non:default-twirl:1.2.3")
    }

}
