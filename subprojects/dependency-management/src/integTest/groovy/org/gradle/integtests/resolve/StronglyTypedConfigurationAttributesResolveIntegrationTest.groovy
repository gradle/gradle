/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve

/**
 * Variant of the configuration attributes resolution integration test which makes use of the strongly typed attributes notation.
 */
class StronglyTypedConfigurationAttributesResolveIntegrationTest extends AbstractConfigurationAttributesResolveIntegrationTest {
    @Override
    String getTypeDefs() {
        '''
            @groovy.transform.Canonical
            class Flavor {
                static Flavor of(String value) { return new Flavor(value:value) }
                String value
                String toString() { value }
            }
            enum BuildType {
                debug,
                release
            }

            def flavor = Attribute.of(Flavor)
            def buildType = Attribute.of(BuildType)

        '''
    }

    @Override
    String getFreeDebug() {
        '(buildType): BuildType.debug, (flavor): Flavor.of("free")'
    }

    @Override
    String getFreeRelease() {
        '(buildType): BuildType.release, (flavor): Flavor.of("free")'
    }

    @Override
    String getDebug() {
        '(buildType): BuildType.debug'
    }

    @Override
    String getFree() {
        '(flavor): Flavor.of("free")'
    }
}
