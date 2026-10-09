/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.tooling.internal.provider.serialization

import org.gradle.internal.classloader.CachingClassLoader
import org.gradle.internal.classloader.ClassLoaderSpec
import org.gradle.internal.classloader.FilteringClassLoader
import org.gradle.internal.classloader.MultiParentClassLoader
import org.gradle.internal.classloader.SystemClassLoaderSpec
import org.gradle.internal.classloader.VisitableURLClassLoader
import org.gradle.internal.serialize.SerializerSpec

class ClassLoaderSpecSerializerTest extends SerializerSpec {
    def serializer = new ClassLoaderSpecSerializer()

    def "round-trips #spec.class.simpleName"() {
        expect:
        def result = serialize(spec, serializer)
        result.class == spec.class

        where:
        spec << [
            SystemClassLoaderSpec.INSTANCE,
            new CachingClassLoader.Spec(),
            new MultiParentClassLoader.Spec(),
            new VisitableURLClassLoader.Spec("app", [new URL("file:/a.jar"), new URL("https://example.test/b.jar")]),
            new ClientOwnedClassLoaderSpec([new URI("file:/a.jar"), new URI("https://example.test/b.jar")]),
        ]
    }

    def "round-trips a filtering spec, preserving its rules"() {
        given:
        def spec = new FilteringClassLoader.Spec(["a.B"], ["a.pkg"], ["a.prefix"], ["res/prefix"], ["res/name"], ["d.C"], ["d.prefix"])

        expect:
        def result = serialize(spec, serializer)
        result instanceof FilteringClassLoader.Spec
        result.classNames == spec.classNames
        result.packageNames == spec.packageNames
        result.packagePrefixes == spec.packagePrefixes
        result.resourcePrefixes == spec.resourcePrefixes
        result.resourceNames == spec.resourceNames
        result.disallowedClassNames == spec.disallowedClassNames
        result.disallowedPackagePrefixes == spec.disallowedPackagePrefixes
    }

    def "refuses to write a spec type it does not know"() {
        when:
        serialize(new ClassLoaderSpec() {}, serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Cannot serialize ClassLoaderSpec of type")
    }

    def "refuses to write a VisitableURLClassLoader.Spec subtype it cannot reconstruct"() {
        when:
        serialize(new SubSpec("app", []), serializer)

        then:
        thrown(IllegalArgumentException)
    }

    def "refuses to read an unknown spec tag"() {
        given:
        def bytes = toBytes(SystemClassLoaderSpec.INSTANCE, serializer)
        bytes[0] = (byte) 99

        when:
        fromBytes(bytes, serializer)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith("Unexpected ClassLoaderSpec tag")
    }

    static class SubSpec extends VisitableURLClassLoader.Spec {
        SubSpec(String name, List<URL> classpath) {
            super(name, classpath)
        }
    }
}
