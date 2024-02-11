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


import spock.lang.Specification

class ConnectorServicesTest extends Specification {

    def "services sharing configuration"() {
        when:
        def connectorOne = ConnectorServices.createConnector()
        def connectorTwo = ConnectorServices.createConnector()

        then:
        connectorOne != connectorTwo

        //below is necessary for some of the thread safety measures we took in the internal implementation
        //it is covered in integrations tests as well, but is not immediately obvious why the concurrent integ test fails hence below assertions

        //tooling impl loader must be shared across connectors, so that we have single DefaultConnection per distro/classpath
        connectorOne.connectionFactory.toolingImplementationLoader == connectorTwo.connectionFactory.toolingImplementationLoader
    }

    def "can close services"() {
        when:
        ConnectorServices.close()
        ConnectorServices.createConnector()

        then:
        IllegalStateException e = thrown()
    }
}
