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
package org.gradle.api.internal.artifacts.ivyservice

import spock.lang.Specification
import org.apache.ivy.core.settings.IvySettings

class DefaultIvyFactoryTest extends Specification {
    final DefaultIvyFactory factory = new DefaultIvyFactory()

    def "creates Ivy instance for IvySettings"() {
        def ivySettings = new IvySettings()

        expect:
        def ivy = factory.createIvy(ivySettings)
        ivy.settings == ivySettings
    }

    def "caches Ivy instance for given IvySettings"() {
        def ivySettings = new IvySettings()

        expect:
        def ivy1 = factory.createIvy(ivySettings)
        def ivy2 = factory.createIvy(ivySettings)
        ivy1.is(ivy2)
    }
}
