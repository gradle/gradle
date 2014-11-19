/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.tooling.internal.consumer

import com.google.common.collect.Lists
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.SetSystemProperties
import org.junit.Rule
import spock.lang.Specification

class DefaultProviderClasspathUpdaterTest extends Specification {
    @Rule final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    @Rule SetSystemProperties sysProp = new SetSystemProperties()
    final ProviderClasspathUpdater cpUpdater = new DefaultProviderClasspathUpdater()

    def "prepends patched class for Gradle distribution with broken ClasspathInferer"() {
        System.properties['java.io.tmpdir'] = tmpDir.createDir('custom-tmp-dir').absolutePath

        when:
        def distDir = tmpDir.createDir('dist')
        distDir.create {
            "dist-2.1" {
                lib {
                    file("a.jar")
                    // file("gradle-core-x.y.jar")
                }
            }
        }
        def coreDir = tmpDir.createDir('core')
        coreDir.file('org/gradle/build-receipt.properties') << """
versionBase=2.1
"""
        coreDir.zipTo(distDir.file('dist-2.1/lib/gradle-core-x.y.jar'))
        def result = Lists.newArrayList(cpUpdater.prependToClasspath(distDir.file('dist-2.1/lib')))

        then:
        result.size() == 1
        def patch = result.get(0)
        patch.name.startsWith('gradle-tooling-patch')
        patch.name.endsWith('.jar')
        patch.path.contains('custom-tmp-dir')
    }
}
