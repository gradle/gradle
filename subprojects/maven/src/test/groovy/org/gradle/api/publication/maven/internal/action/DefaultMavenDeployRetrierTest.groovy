/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.publication.maven.internal.action

import org.apache.http.conn.HttpHostConnectException
import org.apache.maven.artifact.ant.RemoteRepository
import org.gradle.internal.resource.transport.http.HttpErrorStatusCodeException
import org.gradle.testing.internal.util.Specification
import org.sonatype.aether.deployment.DeploymentException
import spock.lang.Unroll

import java.util.concurrent.Callable

@Unroll
class DefaultMavenDeployRetrierTest extends Specification {

    def 'retries operation if transient network issue - #ex'() {
        when:
        int retried = 0
        Callable operation = {
            retried++
            throw ex
        }
        DefaultMavenDeployRetrier defaultMavenDeployRetrier = new DefaultMavenDeployRetrier(operation, Mock(RemoteRepository), 3, 1)
        defaultMavenDeployRetrier.deployWithRetry()

        then:
        retried == 3
        thrown(DeploymentException)

        where:
        ex << [
            new SocketTimeoutException("something went wrong"),
            new HttpHostConnectException(new IOException("something went wrong"), null, null),
            new HttpErrorStatusCodeException("something", "something", 503, "something"),
            new RuntimeException("with cause", new SocketTimeoutException("something went wrong"))
        ]
    }

    def 'does not retry operation if not transient network issue - #ex'() {
        when:
        int retried = 0
        Callable operation = {
            retried++
            throw ex
        }
        DefaultMavenDeployRetrier defaultMavenDeployRetrier = new DefaultMavenDeployRetrier(operation, Mock(RemoteRepository), 3, 1)
        defaultMavenDeployRetrier.deployWithRetry()

        then:
        retried == 1
        thrown(DeploymentException)

        where:
        ex << [
            new RuntimeException("non network issue"),
            new HttpErrorStatusCodeException("something", "something", 400, "something")
        ]
    }
}
