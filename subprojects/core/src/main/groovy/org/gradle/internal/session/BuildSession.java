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

package org.gradle.internal.session;

import net.jcip.annotations.ThreadSafe;
import org.gradle.internal.service.ServiceRegistry;

/**
 * Represents a build "session" which could be a single build or many builds triggered over the lifetime of a
 * continuous build invocation.  Any stoppables added to the build session scope will be stopped at the end of the session.
 */
@ThreadSafe
public interface BuildSession {
    /**
     * Stop and remove any stoppables in the build session.
     */
    void reset();

    /**
     * Returns the build session scoped services for this session.
     *
     * @return the build session service registry
     */
    ServiceRegistry getServices();
}
