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

package org.gradle.internal.resource.transport.gcp.gcs

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpRequest
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.client.util.Sleeper
import com.google.api.services.storage.Storage
import com.google.common.base.Supplier
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class RetryHttpInitializerWrapperTest extends Specification {

    def httpResponse = new MockLowLevelHttpResponse()
    def transport = new MockHttpTransport.Builder()
        .setLowLevelHttpResponse(httpResponse)
        .build()
    def jsonFactory = new GsonFactory()
    def credential = Mock(Credential)
    def credentialSupplier = new Supplier<Credential>() {
        @Override
        Credential get() {
            return credential
        }
    }

    def "initialize should configure request for retries"() {
        given:
        def retryHttpInitializerWrapper = new RetryHttpInitializerWrapper(credentialSupplier)
        def storage = new Storage(transport, jsonFactory, retryHttpInitializerWrapper)
        def retryCount = 0

        when:
        httpResponse.statusCode = 401
        storage.objects().list("test").executeUnparsed()

        then:
        2 * credential.intercept(*_) >> { args ->
            def httpRequest = (args.getAt(0) as HttpRequest)
            assert httpRequest.readTimeout == TimeUnit.MINUTES.toMillis(2)
            assert !httpRequest.loggingEnabled
            assert !httpRequest.curlLoggingEnabled
            assert httpRequest.unsuccessfulResponseHandler != null
            assert httpRequest.getIOExceptionHandler() != null
        }
        2 * credential.handleResponse(*_) >> { args ->
            retryCount++
            return retryCount < 2
        }
        GoogleJsonResponseException e = thrown()
        e.statusCode == 401
    }

    def "initialize should configure request for retries with exponential back-off"() {
        given:
        def sleeper = Mock(Sleeper)
        def retryHttpInitializerWrapper = new RetryHttpInitializerWrapper(credentialSupplier, sleeper)
        def storage = new Storage(transport, jsonFactory, retryHttpInitializerWrapper)
        def retryCount = 0

        when:
        httpResponse.statusCode = 500
        storage.objects().list("test").executeUnparsed()

        then:
        4 * credential.intercept(*_)
        3 * credential.handleResponse(*_) >> { args ->
            retryCount++
            if (retryCount > 2) {
                httpResponse.statusCode = 200
                return true
            }
            return false
        }
        2 * sleeper.sleep(*_)
    }
}
