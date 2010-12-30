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
package org.gradle.api.internal.artifacts.ivyservice

import spock.lang.Specification
import org.junit.Rule
import org.gradle.util.SetSystemProperties
import org.gradle.util.TemporaryFolder

class LocalMavenCacheLocatorTest extends Specification {
    @Rule public final SetSystemProperties systemProperties = new SetSystemProperties()
    @Rule public final TemporaryFolder tmpDir = new TemporaryFolder()
    final LocalMavenCacheLocator locator = new LocalMavenCacheLocator()

    def setup() {
        System.setProperty('user.home', tmpDir.dir.absolutePath)
    }

    def usesDefaultWhenNoSettingsXmlFile() {
        expect:
        locator.localMavenCache == new File(tmpDir.dir, '.m2/repository')
    }

    def usesValueFromSettingsXmlFile() {
        tmpDir.file('.m2/settings.xml') << """
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <localRepository>${tmpDir.file('.m2/custom').absolutePath}</localRepository>
</settings>"""

        expect:
        locator.localMavenCache == new File(tmpDir.dir, '.m2/custom')
    }

    def usesValueWithPlaceholderFromSettingsXmlFile() {
        tmpDir.file('.m2/settings.xml') << '''
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <localRepository>${user.home}/.m2/custom</localRepository>
</settings>'''

        expect:
        locator.localMavenCache == new File(tmpDir.dir, '.m2/custom')
    }
}
