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
import org.gradle.api.internal.artifacts.mvnsettings.DefaultMavenFileLocations
import spock.lang.Specification

class MaybeUserMavenSettingsSupplierTest extends Specification {

    InstallDeployTaskSupport support = Mock()
    def supplier = new MaybeUserMavenSettingsSupplier()

    def "supplies empty settings when user settings not found"() {
        given:
        supplier.emptySettingsSupplier = Mock(EmptyMavenSettingsSupplier)
        supplier.mavenFileLocations = Mock(DefaultMavenFileLocations)

        supplier.mavenFileLocations.getUserSettingsFile() >> { new File('does not exist') }

        when:
        supplier.supply(support)
        supplier.done()

        then:
        1 * supplier.emptySettingsSupplier.supply(support)
        1 * supplier.emptySettingsSupplier.done()
    }

    def "supplies user settings when file exists"() {
        given:
        supplier.emptySettingsSupplier = Mock(EmptyMavenSettingsSupplier)
        supplier.mavenFileLocations = Mock(DefaultMavenFileLocations)

        def concreteFile = File.createTempFile('I exist', ', really')
        concreteFile.deleteOnExit()
        supplier.mavenFileLocations.getUserSettingsFile() >> { concreteFile }

        when:
        supplier.supply(support)
        supplier.done()

        then:
        1 * support.setSettingsFile(concreteFile)
        0 * supplier.emptySettingsSupplier.supply(support)
    }
}
