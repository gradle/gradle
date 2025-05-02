/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resource.gcs.fixtures.stub

import groovy.transform.ToString
import org.gradle.util.internal.ConfigureUtil

@ToString
class HttpStub {
    StubRequest request
    StubResponse response

    static stubInteraction(Closure<?> closure) {
        ConfigureUtil.configure(closure, new HttpStub())
    }

    def request(Closure<?> closure){
        request = new StubRequest()
        ConfigureUtil.configure(closure, request)
    }

    def response(Closure<?> closure){
        response = new StubResponse()
        ConfigureUtil.configure(closure, response)
    }
}
