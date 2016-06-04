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

package org.gradle.plugins.ide.internal.resolver;

import java.util.Collection;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.plugins.ide.internal.resolver.model.UnresolvedIdeRepoFileDependency;

public class UnresolvedDependenciesLogger {

    private final Logger logger = Logging.getLogger(UnresolvedDependenciesLogger.class);

    public void log(Collection<UnresolvedIdeRepoFileDependency> deps) {
        for (UnresolvedIdeRepoFileDependency dep : deps) {
            logger.warn("Could not resolve: " + dep.getDisplayName());
            logger.debug("Could not resolve: " + dep.getDisplayName(), dep.getProblem());
        }
    }
}
