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

package org.gradle.workers.internal

import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import spock.lang.Specification

class FilteringClassLoaderSpecSerializerTest extends Specification {
    def serializer = new FilteringClassLoaderSpecSerializer()
    def outputStream = new ByteArrayOutputStream()
    def encoder = new KryoBackedEncoder(outputStream)

    def "can serialize and deserialize a spec"() {
        def classNames = [ 'allowed.Class1', 'allowed.Class2' ]
        def disallowedClassNames = [ 'disallowed.Class1', 'disallowed.Class2' ]
        def packagePrefixes = [ 'allowed.pkgprefix1.', 'allowed.pkgprefix2' ]
        def disallowedPackagePrefixes = [ 'disallowed.pkgprefix1.', 'disallowed.pkgprefix2.' ]
        def packageNames = [ 'allowed.pkg1', 'allowed.pkg2' ]
        def resourceNames = [ 'allowed.resource1', 'allowed.resource2' ]
        def resourcePrefixes = [ 'allowed.resourcePrefix1.', 'allowed.resourcePrefix2.' ]
        def spec = new FilteringClassLoader.Spec(classNames, packageNames, packagePrefixes, resourcePrefixes, resourceNames, disallowedClassNames, disallowedPackagePrefixes)

        when:
        serializer.write(encoder, spec)
        encoder.flush()

        and:
        def decoder = new KryoBackedDecoder(new ByteArrayInputStream(outputStream.toByteArray()))
        def decodedSpec = serializer.read(decoder)

        then:
        decodedSpec == spec
    }
}
