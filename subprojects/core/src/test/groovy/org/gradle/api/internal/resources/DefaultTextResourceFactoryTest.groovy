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

import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.api.resources.TextResourceFactory
import org.gradle.internal.resource.TextResourceLoader
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue

class DefaultTextResourceFactoryTest extends AbstractProjectBuilderSpec {

    FileOperations fileOperations = Mock(FileOperations.class)
    TemporaryFileProvider tempFileProvider = Mock(TemporaryFileProvider.class)
    TextResourceLoader textResourceLoader = Mock(TextResourceLoader)
    TextResourceFactory textResourceFactory = new DefaultTextResourceFactory(fileOperations, tempFileProvider, textResourceLoader)

    @Issue("gradle/gradle#2663")
    def "creates text resource from uri"() {
        when:
        def textResource = textResourceFactory.fromUri(new URI("http://www.gradle.org/unknown.txt"))

        then:
        1 * textResourceLoader.loadUri("textResource", new URI("http://www.gradle.org/unknown.txt"))

        expect:
        textResource in UriBackedTextResource
    }
}
