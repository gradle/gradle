/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * DSL element used to configure test engines for a {@link JvmTestSuite}.
 *
 * @since 8.3
 */
@HasInternalProtocol
@Incubating
public interface TestEngineContainer extends RegistersTestEngines {
    /**
     * Configures the conventional test engines to use if no other test engines have been registered.  Every time this method is called,
     * the conventions are cleared and replaced with the results of the given action. The provided action is executed immediately,
     * however, any test engines it registers will only be used if no other test engines have been registered on the container.
     */
    void convention(Action<RegistersTestEngines> action);

    /**
     * Registers a configuration rule to be applied to all test engines of a given type.
     * This does not register a test engine of this type, but only configures it if it
     * has been registered or is registered at some point in the future.
     */
    <U extends TestEngineRegistration> void withType(Class<? extends TestEngine<U>> engineClass, Action<U> testEngineRegistrationConfiguration);
}
