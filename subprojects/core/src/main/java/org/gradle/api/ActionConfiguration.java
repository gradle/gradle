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

package org.gradle.api;

/**
 * Allows specification of configuration for some action.
 *
 * @since 4.0
 */
@Incubating
public interface ActionConfiguration {
    /**
     * Adds initialization parameters to use when constructing an instance of the implementation class.
     *
     * @param params - the parameters to use during construction
     */
    void params(Object... params);

    /**
     * Sets any initialization parameters to use when constructing an instance of the implementation class.
     *
     * @param params - the parameters to use during construction
     */
    void setParams(Object... params);

    /**
     * Gets the initialization parameters that will be used when constructing an instance of the implementation class.
     *
     * @return the parameters to use during construction
     */
    Object[] getParams();
}
