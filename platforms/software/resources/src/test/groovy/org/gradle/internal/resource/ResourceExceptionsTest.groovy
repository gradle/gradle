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

package org.gradle.internal.resource

import org.gradle.api.resources.ResourceException
import spock.lang.Specification

class ResourceExceptionsTest extends Specification {
    def "wraps failure"() {
        def location = URI.create("scheme:name")
        def failure = new RuntimeException()

        expect:
        def e = ResourceExceptions.failure(location, "broken", failure)
        e instanceof ResourceException
        e.location == location
        e.message == "broken"
        e.cause == failure
    }

    def "wraps failure with different location"() {
        def location = URI.create("scheme:name")
        def failure = new ResourceException(URI.create("scheme:other"), "cause")

        expect:
        def e = ResourceExceptions.failure(location, "broken", failure)
        e instanceof ResourceException
        e.location == location
        e.message == "broken"
        e.cause == failure
    }

    def "does not wrap failure with same location"() {
        def location = URI.create("scheme:name")
        def failure = new ResourceException(location, "cause")

        expect:
        def e = ResourceExceptions.failure(location, "broken", failure)
        e.is(failure)
    }

    def "does not include the user info of the location in the message"() {
        def location = URI.create("http://user:secret@host/a/b")
        def failure = new RuntimeException()

        expect:
        ResourceExceptions.getFailed(location, failure).message == "Could not get resource 'http://***@host/a/b'."
        ResourceExceptions.getFailed(location, 401, failure).message == "Could not get resource 'http://***@host/a/b'."
        ResourceExceptions.putFailed(location, failure).message == "Could not write to resource 'http://***@host/a/b'."
        ResourceExceptions.getMissing(location).message == "Could not read 'http://***@host/a/b' as it does not exist."
        ResourceExceptions.getMissing(location, failure).message == "Could not read 'http://***@host/a/b' as it does not exist."

        and:
        ResourceExceptions.getFailed(location, failure).location == location
    }
}
