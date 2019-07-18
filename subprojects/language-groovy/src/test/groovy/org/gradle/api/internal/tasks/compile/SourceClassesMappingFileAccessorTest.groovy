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

package org.gradle.api.internal.tasks.compile

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class SourceClassesMappingFileAccessorTest extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'can write then read mapping file'() {
        given:
        Multimap<String, String> mapping = MultimapBuilder.SetMultimapBuilder
            .hashKeys()
            .hashSetValues()
            .build()

        mapping.putAll('MyClass.groovy', ['org.gradle.test.MyClass'])
        mapping.putAll('MyClass2.groovy', ['org.gradle.test.MyClass', 'org.gradle.test.MyClass2'])
        mapping.putAll('MyClass3.grooyy', ['org.gradle.test.MyClass', 'org.gradle.test.MyClass2', 'org.gradle.test.MyClass3'])

        when:
        File file = temporaryFolder.newFile()
        SourceClassesMappingFileAccessor.writeSourceClassesMappingFile(file, mapping)

        then:
        mapping == SourceClassesMappingFileAccessor.readSourceClassesMappingFile(file)
    }
}
