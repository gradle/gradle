/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.integtests.resolve.attributes

/**
 * Variant of the configuration attributes resolution integration test which makes use of the string attributes notation.
 */
class StringConfigurationAttributesResolveIntegrationTest extends AbstractConfigurationAttributesResolveIntegrationTest {

    @Override
    String getTypeDefs() {
        '''
            def flavor = Attribute.of('flavor', String)
            def buildType = Attribute.of('buildType', String)
            def extra = Attribute.of('extra', String)

            allprojects {
               dependencies {
                   attributesSchema {
                      attribute(flavor)
                      attribute(buildType)
                      attribute(extra)
                   }
               }
            }
        '''
    }

    @Override
    String getDebug() {
        "attribute(buildType, 'debug')"
    }

    @Override
    String getFree() {
        "attribute(flavor, 'free')"
    }

    @Override
    String getRelease() {
        "attribute(buildType, 'release')"
    }

    @Override
    String getPaid() {
        "attribute(flavor, 'paid')"
    }
}
