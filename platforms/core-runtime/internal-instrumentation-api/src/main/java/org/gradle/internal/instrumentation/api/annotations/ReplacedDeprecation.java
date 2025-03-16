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

package org.gradle.internal.instrumentation.api.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A configuration for deprecation of a replaced property/accessor.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface ReplacedDeprecation {

    /**
     * Set if deprecation nagging is enabled or not
     */
    boolean enabled() default true;

    /**
     * Corresponds to .willBeRemovedInGradle9() in the DeprecationLogger
     */
    RemovedIn removedIn() default RemovedIn.UNSPECIFIED;

    /**
     * Corresponds to .withUpgradeGuideSection(majorVersion, section) in the DeprecationLogger
     */
    int withUpgradeGuideMajorVersion() default -1;

    /**
     * Corresponds to .withUpgradeGuideSection(majorVersion, section) in the DeprecationLogger
     *
     * withUpgradeGuideSection is only added if withUpgradeGuideMajorVersion is set
     */
    String withUpgradeGuideSection() default "";

    /**
     * Corresponds to .withDslReference() in the DeprecationLogger
     *
     * WithDslReference has lower priority than withUpgradeGuideSection
     */
    boolean withDslReference() default false;

    enum RemovedIn {
        GRADLE9,
        UNSPECIFIED
    }
}
