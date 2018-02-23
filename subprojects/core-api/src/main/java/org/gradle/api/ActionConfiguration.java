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
 * <p>The configuration is represented using zero or more initialization parameters to use when constructing an instance of the implementation class. The following types are supported:</p>
 *
 * <ul>
 *     <li>{@link String}</li>
 *     <li>{@link Boolean}</li>
 *     <li>{@link Integer}, {@link Long}, {@link Short} and other {@link Number} subtypes.</li>
 *     <li>{@link java.io.File}</li>
 *     <li>A {@link java.util.List} or {@link java.util.Set} of any supported type.</li>
 *     <li>An array of any supported type.</li>
 *     <li>A {@link java.util.Map} with keys and values of any supported type.</li>
 *     <li>An {@link Enum} type.</li>
 *     <li>A {@link Named} type created using {@link org.gradle.api.model.ObjectFactory#named(Class, String)}.</li>
 *     <li>Any serializable type.</li>
 * </ul>
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
