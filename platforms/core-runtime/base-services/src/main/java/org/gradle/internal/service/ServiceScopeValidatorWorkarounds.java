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

package org.gradle.internal.service;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.NonNullApi;

import java.util.Set;

/**
 * Temporary workarounds required for the scope validation to work
 * before the service injection framework supports all cases of stricter validation
 * and before all the services are annotated appropriately.
 */
@NonNullApi
public class ServiceScopeValidatorWorkarounds {

    private static final Set<String> SUPPRESSED_VALIDATION_CLASSES = ImmutableSet.of(
        "org.gradle.api.internal.file.FileCollectionFactory",
        "org.gradle.api.problems.Problems"
    );

    public static boolean shouldSuppressValidation(Class<?> serviceType) {
        return SUPPRESSED_VALIDATION_CLASSES.contains(serviceType.getName());
    }

}
