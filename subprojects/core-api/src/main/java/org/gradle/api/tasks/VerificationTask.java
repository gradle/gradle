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
package org.gradle.api.tasks;

import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * A {@code VerificationTask} is a task which performs some verification of the artifacts produced by a build.
 */
public interface VerificationTask {

    /**
     * Specifies whether the build should break when the verifications performed by this task fail.
     * <p>
     * Set this to true to ignore the failures.
     * <p>
     * The default is false.
     */
    @Input
    @ReplacesEagerProperty(
        originalType = boolean.class,
        replacedAccessors = {
            @ReplacedAccessor(value = ReplacedAccessor.AccessorType.GETTER, name = "getIgnoreFailures", originalType = boolean.class),
            @ReplacedAccessor(value = ReplacedAccessor.AccessorType.SETTER, name = "setIgnoreFailures", originalType = boolean.class),
        }
    )
    Property<Boolean> getIgnoreFailures();
}
