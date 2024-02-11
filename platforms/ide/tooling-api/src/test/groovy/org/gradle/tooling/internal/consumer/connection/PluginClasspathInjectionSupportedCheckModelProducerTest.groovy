/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.tooling.internal.consumer.connection

import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.internal.consumer.parameters.ConsumerOperationParameters
import org.gradle.tooling.internal.consumer.versioning.VersionDetails
import spock.lang.Specification

class PluginClasspathInjectionSupportedCheckModelProducerTest extends Specification {

    def delegate = Mock(ModelProducer)

    def "does not fail when feature is unsupported but classpath is empty"() {
        when:
        run("2.7", false)

        then:
        1 * delegate.produceModel(_, _)
    }

    def "fails when feature is unsupported and classpath is not empty"() {
        when:
        run("2.7", true)

        then:
        thrown UnsupportedVersionException
    }

    def "does not fail when version is #version and classpath is empty = #empty"() {
        when:
        run(version, empty)

        then:
        1 * delegate.produceModel(_, _)

        where:
        version << ["2.8-rc-1", "2.8-rc-2", "2.8", "2.9"] * 2
        empty << [true] * 4 + [false] * 4
    }

    void run(String providerVersion, boolean injectedClasspath) {
        def producer = producer(providerVersion)
        producer.produceModel(Void, ConsumerOperationParameters.builder().with {
            entryPoint = "foo"
            if (injectedClasspath) {
                injectedPluginClasspath = DefaultClassPath.of(new File("foo"))
            }
            build()
        })
    }

    ModelProducer producer(String providerVersion) {
        new PluginClasspathInjectionSupportedCheckModelProducer(delegate, VersionDetails.from(providerVersion))
    }
}
