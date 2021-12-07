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

package org.gradle.api.attributes;

import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * Attribute to be used on variants containing the output verification checks (Test data, Jacoco results, etc) which specify the
 * type of verification data.
 * <p>
 * This attribute is usually found on variants that have the {@link Category} attribute valued at {@link Category#VERIFICATION}.
 *
 * @since 7.4
 */
@Incubating
public interface VerificationType extends Named {
    Attribute<VerificationType> VERIFICATION_TYPE_ATTRIBUTE = Attribute.of("org.gradle.verificationtype", VerificationType.class);

    /**
     * A list of directories containing source code, includes code in transitive dependencies
     */
    String MAIN_SOURCES = "main-sources";

    /**
     * Binary results of running tests containing pass/fail information
     */
    String JACOCO_RESULTS = "jacoco-coverage";

    /**
     * Binary test coverage data gathered by JaCoCo
     */
    String TEST_RESULTS = "test-results";
}
