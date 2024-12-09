/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.problems.deprecation;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.problems.internal.Problem;

/**
 * Specialized reporter for deprecation problems.
 *
 * @since 8.12
 */
@Incubating
public interface DeprecationReporter {

    /**
     * Generic deprecation.
     * <p>
     * <b>Note:</b> this is a catch-all deprecation for when no other deprecation type fits.
     * Please use a more specific deprecation type if possible.
     *
     * @param label a label for the deprecation; it should state the deprecation, but not the reason (e.g. "Plugin 'plugin' is deprecated")
     * @param feature a spec to configure the deprecation
     */
    Problem deprecate(String label, Action<DeprecateGenericSpec> feature);

    /**
     * Deprecate a behavior.
     * <p>
     * Behaviors should be used when the unit of code is not deprecated itself,
     * but the usage is.
     * <p>
     * For example, when a method accepts {@link Object} as a parameter to support
     * a wide range of types, a behavior deprecation is fitting to declare that the
     * method should not be used with a specific type anymore.
     *
     * @param label a label for the deprecation; it should state the deprecation, but not the reason (e.g. "Using Object as a parameter is deprecated")
     * @param feature a spec to configure the deprecation
     */
    Problem deprecateBehavior(String label, Action<DeprecateBehaviorSpec> feature);

//    /**
//     * Deprecates the current method.
//     *
//     * @param spec a spec to configure the deprecation
//     */
//    Problem deprecateMethod(Action<DeprecateMethodSpec> spec);
//
//    /**
//     * Deprecate a given method.
//     * <p>
//     * Representation of the signature is created by the called.
//     *
//     * @param method the method to deprecate
//     * @param spec a spec to configure the deprecation
//     */
//    Problem deprecateMethod(String method, Action<DeprecateMethodSpec> spec);


}
