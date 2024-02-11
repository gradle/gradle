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

package org.gradle.integtests.resource.gcs

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.storage.Storage
import com.google.api.services.storage.model.StorageObject
import org.apache.commons.io.IOUtils
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.internal.resource.transport.gcp.gcs.GcsClient
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification

class GcsClientIntegrationTest extends Specification {

    public static final String FILE_NAME = "mavenTest.txt"
    final String bucketName = 'org.gradle.artifacts'

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    @Rule
    final GcsServer server = new GcsServer(temporaryFolder)

    def "should perform put, get and list on an Gcs bucket"() {
        setup:
        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        Storage.Builder builder = new Storage.Builder(new NetHttpTransport(), new GsonFactory(), null)
        builder.setRootUrl(server.uri.toString())
        builder.setServicePath("/")

        GcsClient gcsClient = new GcsClient(builder.build())
        def uri = new URI("gcs://${bucketName}/maven/release/$FILE_NAME")

        when:
        server.stubPutFile(file, bucketName)
        def stream = new FileInputStream(file)

        then:
        gcsClient.put(stream, file.length(), uri)

        when:
        server.stubGetFile(file,"/$bucketName/maven/release/$FILE_NAME")

        then:
        StorageObject object = gcsClient.getResource(uri)
        object.getSize() == fileContents.length()
        object.getEtag() ==~ /\w{32}/
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        IOUtils.copyLarge(gcsClient.getResourceStream(uri), outStream)
        outStream.toString() == fileContents

        when:
        server.stubListFile(temporaryFolder.testDirectory, bucketName)

        then:
        def files = gcsClient.list(new URI("gcs://${bucketName}/maven/release/"))
        !files.isEmpty()
        files.each {
            assert it.contains(".")
        }
    }

    /**
     * Allows for quickly making real Google Cloud Storage requests during development
     */
    @Ignore
    def "should interact with real Gcs"() {
        String bucketName = System.getenv('G_GCS_BUCKET')
        def transport = new NetHttpTransport()
        def jsonFactory = new GsonFactory()
        Storage.Builder builder = new Storage.Builder(transport, jsonFactory, null)
        GoogleCredential googleCredential = GoogleCredential.getApplicationDefault(transport, jsonFactory)
        builder.setHttpRequestInitializer(new HttpRequestInitializer() {
            @Override
            void initialize(HttpRequest request) throws IOException {
                request.setInterceptor(googleCredential)
            }
        })
        builder.setApplicationName("gradle")
        GcsClient gcsClient = new GcsClient(builder.build())

        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        expect:
        def stream = new FileInputStream(file)
        def uri = new URI("gcs://${bucketName}/maven/release/${new Date().getTime()}-mavenTest.txt")
        gcsClient.put(stream, file.length(), uri)
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        IOUtils.copyLarge(gcsClient.getResourceStream(uri), outStream)
        outStream.toString() == fileContents
    }
}
