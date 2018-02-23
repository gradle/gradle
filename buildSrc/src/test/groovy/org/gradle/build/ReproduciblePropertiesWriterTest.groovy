/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.build

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ReproduciblePropertiesWriterTest extends Specification {
    @Rule
    public final TemporaryFolder tempProjectDir = new TemporaryFolder()

    def "newlines are all the same"() {
        def propertiesFile = tempProjectDir.newFile()
        when:
        ReproduciblePropertiesWriter.store([some: 'effect'], propertiesFile, "Oh my")

        then:
        assert propertiesFile.text == '# Oh my\nsome=effect'
    }
}
