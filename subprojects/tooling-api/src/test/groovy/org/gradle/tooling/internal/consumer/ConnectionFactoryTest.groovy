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

import org.gradle.tooling.internal.protocol.ConnectionFactoryVersion3
import org.gradle.tooling.internal.protocol.ConnectionParametersVersion1
import org.gradle.tooling.internal.protocol.ConnectionVersion3
import spock.lang.Specification

class ConnectionFactoryTest extends Specification {
    final ToolingImplementationLoader implementationLoader = Mock()
    final Distribution distribution = Mock()
    final ConnectionFactoryVersion3 connectionImplFactory = Mock()
    final ConnectionVersion3 connectionImpl = Mock()
    final ConnectionParametersVersion1 parameters = Mock()
    final ConnectionFactory factory = new ConnectionFactory(implementationLoader)

    def usesImplementationLoaderToLoadConnectionFactory() {
        when:
        factory.create(distribution, parameters)

        then:
        1 * implementationLoader.create(distribution) >> connectionImplFactory
        1 * connectionImplFactory.create(parameters) >> connectionImpl
        0 * _._
    }
}
