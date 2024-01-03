/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Set;

import static org.gradle.internal.resolve.ResolveExceptionAnalyzer.isCriticalFailure;

@ServiceScope(Scopes.BuildTree.class)
public class ConnectionFailureRepositoryDisabler implements RepositoryDisabler {
    private static final Logger LOGGER = Logging.getLogger(ConnectionFailureRepositoryDisabler.class);

    private final Set<String> disabledRepositories = Sets.newConcurrentHashSet();

    @Override
    public boolean isDisabled(String repositoryId) {
        return disabledRepositories.contains(repositoryId);
    }

    @Override
    public boolean disableRepository(String repositoryId, Throwable throwable) {
        boolean disabled = isDisabled(repositoryId);

        if (disabled) {
            return true;
        }

        if (isCriticalFailure(throwable)) {
            LOGGER.debug("Repository {} has been disabled for this build due to connectivity issues", repositoryId);
            disabledRepositories.add(repositoryId);
            return true;
        }

        return false;
    }

    @VisibleForTesting
    public Set<String> getDisabledRepositories() {
        return disabledRepositories;
    }
}
