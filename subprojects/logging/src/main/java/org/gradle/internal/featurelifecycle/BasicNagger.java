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

/**
 * Try to avoid using methods of this class directly.
 *
 * Use either DeprecationNagger or IncubationNagger obtained from Naggers.
 * Those use a consistent wording for when things will be removed.
 *
 * Obtain an instance by calling Naggers.getBasicNagger().
 *
 * @see Naggers#getIncubationNagger()
 * @see Naggers#getDeprecationNagger()
 * @see Naggers#getBasicNagger()
 */
public interface BasicNagger {
    /**
     * Try to avoid using this nagging method.
     *
     * Use either DeprecationNagger or IncubationNagger obtained from Naggers.
     * Those use a consistent wording for when things will be removed.
     *
     * @see Naggers#getIncubationNagger()
     * @see Naggers#getDeprecationNagger()
     */
    void nagUserWith(String message);

    /**
     * Try to avoid using this nagging method.
     *
     * Use either DeprecationNagger or IncubationNagger obtained from Naggers.
     * Those use a consistent wording for when things will be removed.
     *
     * @see Naggers#getIncubationNagger()
     * @see Naggers#getDeprecationNagger()
     */
    void nagUserOnceWith(String message);
}
