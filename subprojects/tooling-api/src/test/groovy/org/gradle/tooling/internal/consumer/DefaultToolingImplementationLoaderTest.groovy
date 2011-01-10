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
package org.gradle.tooling.internal.consumer

import org.gradle.api.internal.AbstractClassPathProvider
import org.gradle.tooling.internal.provider.DefaultConnectionFactory
import spock.lang.Specification

class DefaultToolingImplementationLoaderTest extends Specification {
    final Distribution distribution = Mock()
    final DefaultToolingImplementationLoader loader = new DefaultToolingImplementationLoader()
    
    def usesMetaInfServiceToDetermineFactoryImplementation() {
        when:
        def factory = loader.create(distribution)

        then:
        factory.class != DefaultConnectionFactory.class
        factory.class.name == DefaultConnectionFactory.class.name
        _ * distribution.toolingImplementationClasspath >> ([AbstractClassPathProvider.getClasspathForClass(DefaultConnectionFactory.class)] as Set)
    }
}