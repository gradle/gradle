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

package org.gradle.api.publication.maven.internal.action;

import org.apache.maven.artifact.ant.RemoteRepository;
import org.gradle.api.internal.artifacts.repositories.transport.NetworkingIssueVerifier;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonatype.aether.deployment.DeploymentException;

import java.util.concurrent.Callable;

public class DefaultMavenDeployRetrier implements MavenDeployRetrier {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMavenDeployRetrier.class);
    private final RemoteRepository remoteRepository;
    private final Callable operation;
    private final int maxDeployAttempts;
    private final int initialBackOff;

    public DefaultMavenDeployRetrier(Callable operation, RemoteRepository remoteRepository, int maxDeployAttempts, int initialBackOff) {
        this.operation = operation;
        this.maxDeployAttempts = maxDeployAttempts;
        this.initialBackOff = initialBackOff;
        assert maxDeployAttempts > 0;
        assert initialBackOff > 0;
        this.remoteRepository = remoteRepository;
    }


    @Override
    public void deployWithRetry() throws DeploymentException {
        int backoff = initialBackOff;
        int retries = 0;
        while (retries < maxDeployAttempts) {
            retries++;
            Throwable failure;
            try {
                operation.call();
                if (retries > 1) {
                    LOGGER.info("Successfully deployed resource after {} retries", retries - 1);
                }
                break;
            } catch (Exception throwable) {
                failure = throwable;
            }
            if (!NetworkingIssueVerifier.isLikelyTransientNetworkingIssue(failure) || retries == maxDeployAttempts) {
                throw new DeploymentException("Could not deploy to remote repository | " + failure.getMessage(), failure);
            } else {
                LOGGER.info("Error while accessing remote repository {}. Waiting {}ms before next retry. {} retries left", remoteRepository, backoff, maxDeployAttempts - retries, failure);
                try {
                    Thread.sleep(backoff);
                    backoff *= 2;
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
    }
}
