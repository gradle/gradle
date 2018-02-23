/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

/**
 * <p>A <code>ResolveException</code> is thrown when dependency resolution fails for some reason.</p>
 */
@Contextual
public class ResolveException extends DefaultMultiCauseException {
    public ResolveException(String resolveContext, Throwable cause) {
        super(buildMessage(resolveContext), cause);
    }

    public ResolveException(String resolveContext, Iterable<? extends Throwable> causes) {
        super(buildMessage(resolveContext), causes);
    }

    private static String buildMessage(String resolveContext) {
        return String.format("Could not resolve all dependencies for %s.", resolveContext);
    }
}
