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

package org.gradle.internal.logging.progress

import org.gradle.internal.operations.BuildOperationContext
import org.gradle.util.Path
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/gradle/gradle/issues/39528")
class WorkerLineOperationTest extends Specification {

    def work = Mock(Runnable)

    def "describes work of the #build"() {
        when:
        def descriptor = new WorkerLineOperation(buildPath, "Run flow action org.example.Foo", "Flow action o.e.Foo", work).description().build()

        then:
        descriptor.displayName == displayName
        descriptor.progressDisplayName == lineText
        descriptor.details == null

        where:
        build            | buildPath             | displayName                                  | lineText
        "root build"     | Path.ROOT             | "Run flow action org.example.Foo"            | "Flow action o.e.Foo"
        "included build" | Path.path(":plugins") | "Run flow action org.example.Foo (:plugins)" | ":plugins > Flow action o.e.Foo"
    }

    @Issue("https://github.com/gradle/gradle/issues/39539")
    def "gives the line text of work of the #build"() {
        expect:
        WorkerLineOperation.lineText(buildPath, "Building task graph") == lineText

        where:
        build            | buildPath             | lineText
        "root build"     | Path.ROOT             | "Building task graph"
        "included build" | Path.path(":plugins") | ":plugins > Building task graph"
    }

    def "runs the work"() {
        when:
        new WorkerLineOperation(Path.ROOT, "Run flow action org.example.Foo", "Flow action o.e.Foo", work).run(Stub(BuildOperationContext))

        then:
        1 * work.run()
    }

    def "lets a failure of the work go through"() {
        given:
        def failure = new RuntimeException("boom")
        work.run() >> { throw failure }

        when:
        new WorkerLineOperation(Path.ROOT, "Run flow action org.example.Foo", "Flow action o.e.Foo", work).run(Stub(BuildOperationContext))

        then:
        def thrown = thrown(RuntimeException)
        thrown.is(failure)
    }
}
