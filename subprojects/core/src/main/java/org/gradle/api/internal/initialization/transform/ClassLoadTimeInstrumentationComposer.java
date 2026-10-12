/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.initialization.transform;

import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Combines Gradle-provided transformations with third-party agents if any are present.
 */
@ServiceScope(Scope.BuildSession.class)
public interface ClassLoadTimeInstrumentationComposer {
    /**
     * Returns a copy of the given classpath carrying the class-load-time transform, when a third-party agent
     * is present in this JVM and the classpath records the instrumentation metadata to rebuild the transform.
     * Returns the classpath unchanged otherwise.
     */
    ClassPath composeWithThirdPartyAgentIfPresent(ClassPath classPath);

    /**
     * Returns a no-op implementation that always return the classpath as is.
     */
    static ClassLoadTimeInstrumentationComposer empty() {
        return classPath -> classPath;
    }
}
