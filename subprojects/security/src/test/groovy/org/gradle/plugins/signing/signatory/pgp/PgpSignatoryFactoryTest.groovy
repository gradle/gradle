/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Issue
import spock.lang.Unroll

class PgpSignatoryFactoryTest extends AbstractProjectBuilderSpec {

    private static final String KEY_ID_VALUE = '24875D73'
    private static final String PASSWORD_VALUE = 'secret'
    private static final String SECRET_KEY_RING_FILE = '/Users/me/.gnupg/secring.gpg'
    def factory = new PgpSignatoryFactory()

    @Issue("https://github.com/gradle/gradle/issues/2267")
    @Unroll
    def "property '#nullPropertyName' with null value throws a descriptive exception if required"() {
        given:
        project.ext.'signing.keyId' = keyId
        project.ext.'signing.password' = password
        project.ext.'signing.secretKeyRingFile' = secretKeyRingFile

        when:
        factory.createSignatory(project, true)

        then:
        def t = thrown(InvalidUserDataException)
        t.message == "property '$nullPropertyName' was null. A valid value is needed for signing"

        where:
        keyId        | password       | secretKeyRingFile    | nullPropertyName
        null         | PASSWORD_VALUE | SECRET_KEY_RING_FILE | 'signing.keyId'
        KEY_ID_VALUE | null           | SECRET_KEY_RING_FILE | 'signing.password'
        KEY_ID_VALUE | PASSWORD_VALUE | null                 | 'signing.secretKeyRingFile'
    }

    @Issue("https://github.com/gradle/gradle/issues/2267")
    @Unroll
    def "returns null signatory if any of the properties is null and not required"() {
        given:
        project.ext.'signing.keyId' = keyId
        project.ext.'signing.password' = password
        project.ext.'signing.secretKeyRingFile' = secretKeyRingFile

        when:
        def signatory = factory.createSignatory(project, false)

        then:
        !signatory

        where:
        keyId        | password       | secretKeyRingFile
        null         | PASSWORD_VALUE | SECRET_KEY_RING_FILE
        KEY_ID_VALUE | null           | SECRET_KEY_RING_FILE
        KEY_ID_VALUE | PASSWORD_VALUE | null
    }

    @Unroll
    def "undeclared property '#missingPropertyName' throws a descriptive exception if required"() {
        given:
        declaredProperties.each {
            project.ext."$it" = 'someValue'
        }

        when:
        factory.createSignatory(project, true)

        then:
        def t = thrown(InvalidUserDataException)
        t.message == "property '$missingPropertyName' could not be found on project and is needed for signing"

        where:
        declaredProperties                                | missingPropertyName
        ['signing.password', 'signing.secretKeyRingFile'] | 'signing.keyId'
        ['signing.keyId', 'signing.secretKeyRingFile']    | 'signing.password'
        ['signing.keyId','signing.password']              | 'signing.secretKeyRingFile'
    }
}
