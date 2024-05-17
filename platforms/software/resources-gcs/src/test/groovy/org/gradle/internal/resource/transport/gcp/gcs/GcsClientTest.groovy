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

import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import com.google.api.services.storage.model.Objects
import org.gradle.api.resources.ResourceException
import spock.lang.Specification

class GcsClientTest extends Specification {

    def "Should upload to gcs"() {
        given:
        Storage storage = Mock(Storage)
        GcsClient client = new GcsClient(storage)
        URI uri = new URI("gcs://localhost/maven/snapshot/myFile.txt")

        when:
        client.put(Mock(InputStream), 12L, uri)
        then:
        1 * storage.objects(*_) >> Mock(Storage.Objects) {
            1 * insert(*_) >> { args ->
                assert (args.getAt(0) as String) == 'localhost'
                assert (args[1] as StorageObject).getName() == 'maven/snapshot/myFile.txt'
                assert (args[2] as InputStreamContent).length == 12
                return Mock(Storage.Objects.Insert) {
                    1 * execute()
                }
            }
        }
    }

    def "should make batch call when more than one object listing page exists"() {
        def gcsStorageClient = Mock(Storage)
        GcsClient gcsClient = new GcsClient(gcsStorageClient)
        def uri = new URI("gcs://mybucket.com.au/maven/release/")

        when:
        gcsClient.list(uri)

        then:
        1 * gcsStorageClient.objects(*_) >> Mock(Storage.Objects) {
            1 * list(uri.getHost()) >> {
                return Mock(Storage.Objects.List) {
                    def self = it
                    int page = 0
                    int maxPages = 2
                    setPrefix(*_) >> { args ->
                        assert args.get(0).startsWith(uri.getPath().replaceAll("^/+", ''))
                        return self
                    }
                    maxPages * execute() >> {
                        Objects objects = new Objects()
                        objects.setItems(Collections.singletonList(new StorageObject()))
                        objects.setNextPageToken(page < maxPages - 1 ? Integer.toString(page) : null)
                        page++
                        return objects
                    }
                    maxPages * setPageToken(_)
                }
            }
        }
    }

    def "should include uri when file not found"() {
        def gcsStorageClient = Mock(Storage)
        URI uri = new URI("https://somehost/file.txt")
        GcsClient gcsClient = new GcsClient(gcsStorageClient)

        gcsStorageClient.objects(*_) >> Mock(Storage.Objects) {
            get(*_) >> Mock(Storage.Objects.Get) {
                execute() >> { throw Mock(GoogleJsonResponseException) {
                    getStatusCode() >> 404
                } }
            }
        }

        when:
        gcsClient.getResource(uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not get resource 'https://somehost/file.txt'")
    }

    def "should include uri when upload fails"() {
        def gcsStorageClient = Mock(Storage)
        URI uri = new URI("https://somehost/file.txt")
        GcsClient gcsClient = new GcsClient(gcsStorageClient)

        gcsStorageClient.objects(*_) >> Mock(Storage.Objects) {
            insert(*_) >> Mock(Storage.Objects.Insert) {
                execute() >> { throw new IOException() }
            }
        }

        when:
        gcsClient.put(Mock(InputStream), 0, uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not write to resource 'https://somehost/file.txt'")
    }

    def "should not fail on GcsClient.list() returning null"() {
        def gcsStorageClient = Mock(Storage)
        GcsClient gcsClient = new GcsClient(gcsStorageClient)
        def uri = new URI("gcs://mybucket.com.au/maven/release/")
        when:
        gcsClient.list(uri)
        then:
        1 * gcsStorageClient.objects(*_) >> Mock(Storage.Objects) {
            1 * list(uri.getHost()) >> {
                return Mock(Storage.Objects.List) {
                    def self = it
                    execute() >> {
                        return new Objects().setItems(null)
                    }
                    setPrefix(*_) >> { args ->
                        assert args.get(0).startsWith(uri.getPath().replaceAll("^/+", ''))
                        return self
                    }
                }
            }
        }
    }

    def credentials() {
        def credentials = new MockGoogleCredential()
        credentials.setAccessToken("Access")
        credentials.setRefreshToken("Refresh")
        credentials
    }
}
