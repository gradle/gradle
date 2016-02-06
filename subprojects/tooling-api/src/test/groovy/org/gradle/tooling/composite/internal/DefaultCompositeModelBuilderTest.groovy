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

package org.gradle.tooling.composite.internal

import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.tooling.*
import org.gradle.tooling.model.Model

class DefaultCompositeModelBuilderTest extends ConcurrentSpec  {

    def "retrieves a model over a ProjectConnection"() {
        given:
        Model modelResult = Mock()
        GradleParticipantBuild participant = successful(modelResult)
        def modelBuilder = modelBuilder(participant)
        when:
        def result = modelBuilder.get()
        then:
        result == [ modelResult ] as Set
    }

    def "fails overall if an underlying ProjectConnection fails"() {
        given:
        def failure = new BuildException("message", null)
        GradleParticipantBuild participant = failing(failure)
        def modelBuilder = modelBuilder(participant)

        when:
        def result = modelBuilder.get()
        then:
        def e = thrown(BuildException)
        e == failure
    }

    def "retrieves a set of models from multiple participants"() {
        given:
        def participants = []
        def modelResults = [] as Set
        (0..3).collect {
            Model modelResult = Mock()
            participants << successful(modelResult)
            modelResults << modelResult
        }

        def modelBuilder = modelBuilder(*participants)
        when:
        def result = modelBuilder.get()
        then:
        result.size() == 4
        result.containsAll(modelResults)
    }

    def "throws failure when a participant connection fails"() {
        given:
        def participants = []
        def modelResults = [] as Set
        def failure = new BuildException("message", null)
        (0..2).collect {
            Model modelResult = Mock()
            participants << successful(modelResult)
            modelResults << modelResult
        }
        participants << failing(failure)
        def modelBuilder = modelBuilder(*participants)
        when:
        def result = modelBuilder.get()
        then:
        def e = thrown(BuildException)
        e == failure
    }

    def "throws first failure"() {
        given:
        def participants = []
        def firstFailure = new BuildException("message-first", null)
        participants << failing(firstFailure)
        def secondFailure = new BuildException("message-second", null)
        participants << failing(secondFailure, { thread.block() })
        def modelBuilder = modelBuilder(*participants)
        when:
        def result = modelBuilder.get()
        then:
        def e = thrown(BuildException)
        e == firstFailure
    }

    def "only calls result handler onFailure once"() {
        given:
        def participants = []
        def failure = new BuildException("message-first", null)
        participants << failing(failure)
        participants << successful(Mock(Model))
        participants << successful(Mock(Model))
        def modelBuilder = modelBuilder(*participants)
        ResultHandler resultHandler = Mock()
        ResultHandler wrapped = new ResultHandler() {
            @Override
            void onComplete(Object result) {
                resultHandler.onComplete(result)
                instant.received
            }

            @Override
            void onFailure(GradleConnectionException f) {
                resultHandler.onFailure(f)
                instant.received
            }
        }
        when:
        modelBuilder.get(wrapped)
        thread.blockUntil.received
        then:
        1 * resultHandler.onFailure(failure)
        0 * resultHandler._
    }

    def "only calls result handler onComplete once"() {
        given:
        def participants = []
        participants << successful(Mock(Model))
        participants << successful(Mock(Model))
        participants << successful(Mock(Model))
        def modelBuilder = modelBuilder(*participants)
        ResultHandler resultHandler = Mock()
        ResultHandler wrapped = new ResultHandler() {
            @Override
            void onComplete(Object result) {
                resultHandler.onComplete(result)
                instant.received
            }

            @Override
            void onFailure(GradleConnectionException f) {
                resultHandler.onFailure(f)
                instant.received
            }
        }
        when:
        modelBuilder.get(wrapped)
        thread.blockUntil.received
        then:
        1 * resultHandler.onComplete(_ as Set)
        0 * resultHandler._
    }

    GradleParticipantBuild successful(result) {

        ModelBuilder projectModelBuilder = Mock() {
            get(_) >> { args ->
                start {
                    args[0].onComplete(result)
                }
            }
        }

        ProjectConnection connection = Mock() {
            model(Model) >> projectModelBuilder
        }

        GradleParticipantBuild participant = Mock() {
            getConnection() >> connection
        }

        return participant
    }

    GradleParticipantBuild failing(GradleConnectionException failure, Closure before={}) {
        ModelBuilder projectModelBuilder = Mock() {
            get(_) >> { args ->
                start {
                    before()
                    args[0].onFailure(failure)
                }
            }
        }

        ProjectConnection connection = Mock() {
            model(Model) >> projectModelBuilder
        }

        GradleParticipantBuild participant = Mock() {
            getConnection() >> connection
        }

        return participant
    }

    def modelBuilder(GradleParticipantBuild... participants) {
        new DefaultCompositeModelBuilder(Model, participants as Set)
    }
}
