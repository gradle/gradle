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

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

/**
 * <p>An opaque exception, thrown when dependency resolution fails for some reason.</p>
 *
 * <strong>This type should only be extended and/or thrown by Gradle internal code.</strong>
 */
@Contextual
@HasInternalProtocol
public class ResolveException extends DefaultMultiCauseException {

    /**
     * Do not call this constructor.
     *
     * @deprecated This constructor will be removed in 9.0
     */
    @Deprecated
    public ResolveException(String message, Throwable cause) {
        super(message, cause);

        DeprecationLogger.deprecateAction("Directly instantiating a ResolveException")
            .withContext("Instantiating this exception is reserved for Gradle internal use only.")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser();
    }

    /**
     * Do not call this constructor.
     *
     * @deprecated This constructor will be made protected in 9.0
     */
    @Deprecated
    public ResolveException(String message, Iterable<? extends Throwable> causes) {
        super(message, causes);

        DeprecationLogger.deprecateAction("Directly instantiating a ResolveException")
            .withContext("Instantiating this exception is reserved for Gradle internal use only.")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser();
    }

    /**
     * The actual constructor called by concrete resolve exception implementations. Should not be
     * called except from Gradle internal code.
     *
     * <p>This constructor accepts a dummy parameter since we cannot call the constructor without it,
     * as that emits a deprecation warning. In 9.0, we can change the above constructor to protected
     * and remove this constructor.</p>
     *
     * @since 8.9
     */
    @Incubating
    protected ResolveException(String message, Iterable<? extends Throwable> causes, @SuppressWarnings("unused") boolean dummy) {
        super(message, causes);
    }
}
