/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.transport

import org.apache.http.ConnectionClosedException
import org.apache.http.NoHttpResponseException
import org.apache.http.conn.HttpHostConnectException
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.resource.transport.http.HttpErrorStatusCodeException
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

@Subject(NetworkingIssueVerifier)
class NetworkingIssueVerifierTest extends Specification {

    @Unroll("'#description' is likely transient network issue")
    def "verifies if an exception is a related to transient network issue"() {
        expect:
        NetworkingIssueVerifier.isLikelyTransientNetworkingIssue(failure)

        where:
        description                                                 | failure
        "SocketException"                                           | new SocketException()
        "SocketTimeoutException"                                    | new SocketTimeoutException()
        "NoHttpResponseException"                                   | new NoHttpResponseException("something went wrong")
        "ConnectionClosedException"                                 | new ConnectionClosedException("something went wrong")
        "HttpHostConnectException"                                  | new HttpHostConnectException(new IOException("something went wrong"), null, null)
        "DefaultMultiCauseException"                                | new DefaultMultiCauseException("something went wrong", new SocketTimeoutException())
        "HttpErrorStatusCodeException with server error"            | new HttpErrorStatusCodeException("something", "something", 503, "something")
        "HttpErrorStatusCodeException with transient client error"  | new HttpErrorStatusCodeException("something", "something", 429, "something")
        "RuntimeException with a likely network exception as cause" | new RuntimeException("with cause", new SocketTimeoutException("something went wrong"))
    }
}
