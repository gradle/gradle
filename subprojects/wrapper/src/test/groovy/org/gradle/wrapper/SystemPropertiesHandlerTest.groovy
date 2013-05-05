/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.wrapper

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class SystemPropertiesHandlerTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def parsesPropertiesFile() {
        File propFile = tmpDir.file('props')
        Properties props = new Properties()
        props.putAll a: 'b', 'systemProp.c': 'd', 'systemProp.': 'e'
        props.store(new FileOutputStream(propFile), "")
        
        expect:
        [c: 'd'] == SystemPropertiesHandler.getSystemProperties(propFile)
    }

    def ifNoPropertyFileExistShouldReturnEmptyMap() {
        expect:
        [:] == SystemPropertiesHandler.getSystemProperties(tmpDir.file('unknown'))
    }
}
