/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.api.artifacts.repositories.AwsCredentials
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager
import org.gradle.api.internal.file.TemporaryFileProvider
import org.gradle.internal.resource.cached.CachedExternalResourceIndex
import org.gradle.logging.ProgressLoggerFactory
import org.gradle.util.BuildCommencedTimeProvider
import spock.lang.Specification
import spock.lang.Unroll

class S3TransportBuilderTest extends Specification {

    @Unroll
    def "should set #property property"() {
        S3TransportBuilder builder = new S3TransportBuilder()
        expect:
        builder."${property}"(value)."${property}" == value

        where:
        property                      | value
        'name'                        | 'test'
        'awsCredentials'              | Mock(AwsCredentials)
        'progressLoggerFactory'       | Mock(ProgressLoggerFactory)
        'temporaryFileProvider'       | Mock(TemporaryFileProvider)
        'cachedExternalResourceIndex' | Mock(CachedExternalResourceIndex)
        'timeProvider'                | Mock(BuildCommencedTimeProvider)
        'cacheLockingManager'         | Mock(CacheLockingManager)
    }
}
