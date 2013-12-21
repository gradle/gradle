/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.announce.internal

import org.gradle.api.internal.GradleDistributionLocator
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultIconProviderTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final GradleDistributionLocator locator = Mock()
    final DefaultIconProvider provider = new DefaultIconProvider(locator)
    
    def "returns png file that matches specified dimensions exactly"() {
        given:
        def homeDir = tmpDir.testDirectory
        def pngFile = tmpDir.createFile("media/gradle-icon-48x18.png")
        _ * locator.gradleHome >> homeDir

        expect:
        provider.getIcon(48, 18) == pngFile
    }
    
    def "returns null when no distribution home"() {
        given:
        _ * locator.gradleHome >> null

        expect:
        provider.getIcon(48, 18) == null
    }

    def "returns null when no icon with exact dimension"() {
        given:
        def homeDir = tmpDir.testDirectory
        _ * locator.gradleHome >> homeDir

        expect:
        provider.getIcon(48, 18) == null
    }
}
