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

package org.gradle.language.swift.internal

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.file.FileOperations
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultSwiftApplicationTest extends Specification {
    def app = new DefaultSwiftApplication("main", Mock(ProjectLayout), TestUtil.objectFactory(), Stub(FileOperations), Stub(ConfigurationContainer))

    def "can create executable binary"() {
        expect:
        def binary = app.createExecutable("debug", true, false, true)
        binary.name == "mainDebug"
        binary.debuggable
        !binary.optimized
        binary.testable

        app.binaries.realizeNow()
        app.binaries.get() == [binary] as Set
    }

    def "throws exception when development binary is not available"() {
        given:
        app.binaries.realizeNow()

        when:
        app.developmentBinary.get()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "No value has been specified for this provider."
    }
}
