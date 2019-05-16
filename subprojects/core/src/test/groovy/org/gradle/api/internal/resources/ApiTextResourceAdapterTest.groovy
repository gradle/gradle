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

package org.gradle.api.internal.resources

import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.internal.resource.TextResource
import org.gradle.internal.resource.TextUrlResourceLoader

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class ApiTextResourceAdapterTest extends AbstractTextResourceTest {

    TextResource textResource = Mock(TextResource)
    TextUrlResourceLoader textResourceLoader = Mock(TextUrlResourceLoader)
    Reader reader

    def setup() {
        def file = project.file("file.txt")
        file.text = "contents"
        reader = new InputStreamReader(new FileInputStream(file), Charset.defaultCharset())

        textResourceLoader.loadUri("textResource", new URI("https://www.gradle.org/unknown.txt")) >> textResource
        textResource.getFile() >>> [file, null]
        textResource.getDisplayName() >> "Text resource display name"
        textResource.getText() >>> ["contents", "more contents"]
        textResource.getCharset() >> StandardCharsets.UTF_8
        textResource.getAsReader() >> reader

        resource = new ApiTextResourceAdapter(textResourceLoader, project.services.get(TemporaryFileProvider), new URI("https://www.gradle.org/unknown.txt"))
    }

    def cleanup() {
        reader.close()
    }

    def "get display name"() {
        expect:
        resource.getDisplayName() == "Text resource display name"
    }

    def "to string"() {
        expect:
        resource.toString() == "Text resource display name"
    }

    def "get build dependencies"() {
        expect:
        resource.getBuildDependencies() == TaskDependencyInternal.EMPTY
    }

    def "get input Files"() {
        expect:
        resource.getInputFiles() == null
    }

    def "get input properties"() {
        expect:
        resource.getInputProperties() == new URI("http://www.gradle.org/unknown.txt")
    }

    def "read as file when file in depending resource is null"() {
        expect:
        resource.asString() == "contents"
        resource.asFile().text == "contents"
        resource.asFile().text == "more contents"
    }
}
