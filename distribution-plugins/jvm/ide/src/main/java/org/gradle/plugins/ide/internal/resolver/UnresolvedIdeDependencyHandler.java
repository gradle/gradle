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

import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.util.Collection;

public class UnresolvedIdeDependencyHandler {

    private final Logger logger = Logging.getLogger(UnresolvedIdeDependencyHandler.class);

    public void log(Collection<UnresolvedDependencyResult> deps) {
        for (UnresolvedDependencyResult dep : deps) {
            log(dep);
        }
    }

    public void log(UnresolvedDependencyResult dep) {
        logger.warn("Could not resolve: " + dep.getAttempted());
        logger.debug("Could not resolve: " + dep.getAttempted(), dep.getFailure());
    }

    public File asFile(UnresolvedDependencyResult dep, File parent) {
        return new File(parent, "unresolved dependency - " + dep.getAttempted().getDisplayName().replaceAll(":", " "));
    }
}
