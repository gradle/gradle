/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal

import org.gradle.internal.jvm.Jvm
import spock.lang.Specification

class CurrentInstallationSupplierTest extends Specification {

    def "supplies java home as installation"() {
        given:
        def supplier = new CurrentInstallationSupplier()

        when:
        def directories = supplier.get()

        then:
        directories.size() == 1
        directories[0].location == Jvm.current().javaHome
        directories[0].source == "Current JVM"
    }
}
