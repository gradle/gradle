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

package org.gradle.integtests.resource.s3.fixtures.stub

import groovy.transform.ToString

@ToString
class HttpStub {
    StubRequest request
    StubResponse response

    static stubInteraction(Closure<?> closure) {
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        HttpStub stub = new HttpStub()
        closure.delegate = stub
        closure()
        stub
    }

    def request(Closure<?> closure){
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        StubRequest stubRequest = new StubRequest()
        closure.delegate = stubRequest
        closure()
        request = stubRequest
    }

    def response(Closure<?> closure){
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        StubResponse stubResponse= new StubResponse()
        closure.delegate = stubResponse
        closure()
        response = stubResponse
    }
}
