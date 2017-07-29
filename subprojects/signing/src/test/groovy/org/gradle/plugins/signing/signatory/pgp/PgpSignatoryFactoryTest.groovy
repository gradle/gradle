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

package org.gradle.plugins.signing.signatory.pgp

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.*

@Issue("https://github.com/gradle/gradle/issues/2267")
class PgpSignatoryFactoryTest extends Specification {

    protected Project aProjectWithNullProperties(){
        def project = new ProjectBuilder().build()
        project.ext.'signing.keyId' = null
        project.ext.'signing.password' = null
        project.ext.'signing.secretKeyRingFile' = null
        project
    }

    def "null properties do not throw an NPE"() {
        setup:
            def factory = new PgpSignatoryFactory()
            def project = aProjectWithNullProperties()
        when:
            factory.createSignatory(project, true)
        then:
            //notThrown NullPointerException // does not allow the real exception through
            Exception ex = thrown()
            ex.class != NullPointerException
    }
    def "null properties throw a nice exception"() {
        setup:
            def factory = new PgpSignatoryFactory()
            def project = aProjectWithNullProperties()
        when:
            factory.createSignatory(project, true)
        then:
            thrown InvalidUserDataException
    }


}
