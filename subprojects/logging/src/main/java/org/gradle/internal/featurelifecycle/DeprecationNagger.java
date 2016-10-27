/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

import org.gradle.util.GradleVersion;

/**
 * Use this to nag the user with deprecation messages.
 *
 * Obtain an instance by calling Naggers.getDeprecationNagger().
 *
 * @see Naggers#getDeprecationNagger()
 */
public interface DeprecationNagger extends NextMajorVersionDeprecationNagger {
    /**
     * Avoid using this method, use the variant with an explanation instead.
     */
    void nagUserOfDeprecated(String thing, GradleVersion gradleVersion);

    void nagUserOfDeprecated(String thing, String explanation, GradleVersion gradleVersion);

    void nagUserOfDeprecatedBehaviour(String behaviour, GradleVersion gradleVersion);

    void nagUserOfDiscontinuedApi(String api, String advice, GradleVersion gradleVersion);

    void nagUserOfDiscontinuedMethod(String methodName, GradleVersion gradleVersion);

    void nagUserOfDiscontinuedMethod(String methodName, String advice, GradleVersion gradleVersion);

    void nagUserOfDiscontinuedProperty(String propertyName, String advice, GradleVersion gradleVersion);

    void nagUserOfPluginReplacedWithExternalOne(String pluginName, String replacement, GradleVersion gradleVersion);

    void nagUserOfReplacedMethod(String methodName, String replacement, GradleVersion gradleVersion);

    void nagUserOfReplacedNamedParameter(String parameterName, String replacement, GradleVersion gradleVersion);

    void nagUserOfReplacedPlugin(String pluginName, String replacement, GradleVersion gradleVersion);

    void nagUserOfReplacedProperty(String propertyName, String replacement, GradleVersion gradleVersion);

    void nagUserOfReplacedTask(String taskName, String replacement, GradleVersion gradleVersion);

    void nagUserOfReplacedTaskType(String taskName, String replacement, GradleVersion gradleVersion);
}
