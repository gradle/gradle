/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ResolveException;

public class ArtifactResolveException extends ResolveException {
    private final String type;
    private final String displayName;

    public ArtifactResolveException(String type, String displayName, Iterable<? extends Throwable> failures) {
        super(displayName, failures);
        this.type = type;
        this.displayName = displayName;
    }

    // Need to override as error message is hardcoded in constructor of public type ResolveException
    @Override
    public String getMessage() {
        return String.format("Could not resolve all %s for %s.", type, displayName);
    }
}
