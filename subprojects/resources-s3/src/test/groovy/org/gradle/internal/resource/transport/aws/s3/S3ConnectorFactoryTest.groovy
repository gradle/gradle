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

package org.gradle.internal.resource.transport.aws.s3

import org.gradle.api.credentials.AwsCredentials
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.authentication.DefaultAwsImAuthentication
import org.gradle.internal.authentication.AllSchemesAuthentication
import spock.lang.Specification

class S3ConnectorFactoryTest extends Specification {

    S3ConnectorFactory factory = new S3ConnectorFactory()
    def "fails when no aws credentials provided for auth"() {
        setup:
        def resourceConnectorSpecification = Mock(ResourceConnectorSpecification)
        1 * resourceConnectorSpecification.getCredentials(AwsCredentials) >> null
        1 * resourceConnectorSpecification.getAuthentications() >> [ new AllSchemesAuthentication(null) ]
        
        when:
        factory.createResourceConnector(resourceConnectorSpecification)

        then:
        def e = thrown(IllegalArgumentException)
        e.message ==  "AwsCredentials must be set for S3 backed repository."
    }

        def "ok when no aws credentials provided for Iam Auth"() {
        setup:
        def resourceConnectorSpecification = Mock(ResourceConnectorSpecification)
        0 * resourceConnectorSpecification.getCredentials(AwsCredentials) >> null
        1 * resourceConnectorSpecification.getAuthentications() >> [ new DefaultAwsImAuthentication(null) ]
        
        when:
        def resourceConnector = factory.createResourceConnector(resourceConnectorSpecification)

        then:
        resourceConnector.class == S3ResourceConnector
    }

}
