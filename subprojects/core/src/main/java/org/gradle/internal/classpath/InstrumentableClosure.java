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

package org.gradle.internal.classpath;

import org.gradle.api.NonNullApi;

/**
 * Represents a closure that can participate in dynamic calls instrumentation.
 * Initially, such a closure may not be completely prepared for participating in dynamic call interception,
 * which is a performance optimization. <p>
 *
 * Upon an invocation of {@link InstrumentableClosure#makeEffectivelyInstrumented} the instance must perform
 * all the delayed work and become "effectively instrumented".
 *
 * @see CallInterceptionClosureInstrumentingClassVisitor
 */
@NonNullApi
public interface InstrumentableClosure {
    void makeEffectivelyInstrumented();
}
