/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.attributes.java;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;

/**
 * Represents the target JVM environment. Typically, a standard JVM or Android.
 * This attribute can be used by libraries to indicate that a certain variant is better suited for
 * a certain JVM environment. It does however NOT strictly require environments to match, as the
 * general assumption is that Java libraries can also run on environments they are not optimized for.
 *
 * @since 7.0
 */
@Incubating
public interface TargetJvmEnvironment extends Named {
    Attribute<TargetJvmEnvironment> TARGET_JVM_ENVIRONMENT_ATTRIBUTE = Attribute.of("org.gradle.jvm.environment", TargetJvmEnvironment.class);

    /**
     * A standard JVM environment (e.g. running on desktop or server machines).
     */
    String STANDARD_JVM = "standard-jvm";

    /**
     * An Android environment.
     */
    String ANDROID = "android";
}
