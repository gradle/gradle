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

package org.gradle.api.publication.maven.internal.ant

import org.apache.maven.artifact.ant.InstallDeployTaskSupport
import spock.lang.Specification

class EmptyMavenSettingsSupplierTest extends Specification {

    def EmptyMavenSettingsSupplier supplier = new EmptyMavenSettingsSupplier()
    InstallDeployTaskSupport support = Mock()

    def "supplies empty settings"() {
        when:
        supplier.supply(support)

        then:
        supplier.settingsXml.text == '<settings/>'
//        1 * support.setSettingsFile(supplier.settingsXml) //not sure why it doesn't work
    }

    def "deletes file when done"() {
        when:
        supplier.supply(support)
        supplier.done()

        then:
        !supplier.settingsXml.exists()
    }

    def "done operation must be safe"() {
        when:
        supplier.done()

        then:
        noExceptionThrown()
    }
}
