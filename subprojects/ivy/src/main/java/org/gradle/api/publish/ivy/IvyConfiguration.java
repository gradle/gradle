/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy;

import org.gradle.api.Named;

import java.util.Set;

/**
 * A configuration included in an {@link IvyPublication}, which will be published in the ivy descriptor file generated.
 */
public interface IvyConfiguration extends Named {

    /**
     * Add the name of a configuration that this configuration extends.
     * The extend value can use the following wildcards:
     * <ul>
     *     <li>* - all other configurations</li>
     *     <li>*(public) - all other public configurations</li>
     *     <li>*(private) - all other private configurations</li>
     * </ul>
     * @param configuration The extended configuration name
     */
    void extend(String configuration);

    /**
     * The set of names of extended configurations, added via {@link #extend(String)}.
     *
     * @return The names of extended configurations.
     */
    Set<String> getExtends();
}
