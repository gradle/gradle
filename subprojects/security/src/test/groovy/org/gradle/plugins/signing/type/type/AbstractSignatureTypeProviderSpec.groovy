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
package org.gradle.plugins.signing.type.type

import org.gradle.api.InvalidUserDataException
import org.gradle.plugins.signing.type.AbstractSignatureType
import org.gradle.plugins.signing.type.AbstractSignatureTypeProvider
import spock.lang.Specification

class AbstractSignatureTypeProviderSpec extends Specification {

    static type1 = new AbstractSignatureType() { String getExtension() { "1" } }
    static type2 = new AbstractSignatureType() { String getExtension() { "2" } }
    static type3 = new AbstractSignatureType() { String getExtension() { "3" } }

    def provider = new AbstractSignatureTypeProvider() {{
        register(AbstractSignatureTypeProviderSpec.type1)
        register(AbstractSignatureTypeProviderSpec.type2)
        setDefaultType(AbstractSignatureTypeProviderSpec.type1.extension)
    }}

    def "has check"() {
        expect:
        provider.hasTypeForExtension(type1.extension)
        provider.hasTypeForExtension(type2.extension)
        !provider.hasTypeForExtension(type3.extension)
    }

    def "default type"() {
        expect:
        provider.defaultType == type1

        when:
        provider.defaultType = type2.extension

        then:
        provider.defaultType == type2
    }

    def "can't set default type to unregistered type"() {
        when:
        provider.defaultType = type3.extension

        then:
        thrown InvalidUserDataException
    }

}
