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

package org.gradle.internal.jvm

import org.gradle.api.internal.file.TestFiles
import spock.lang.Specification

class JavaInstallationsDirLocatorTest extends Specification {

    def "discovered java installations contains current"() {
        given:
        def discoverer = JavaInstallationsDirLocator.withDefaultStrategies(TestFiles.execActionFactory())

        when:
        def discovered = discoverer.findJavaInstallationsDirs()

        then:
        discovered.contains(Jvm.current().javaHome)
    }

}
