/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.artifacts.ivy;

import javax.annotation.Nullable;

/**
 * The metadata about an Ivy module that acts as an input to a component metadata rule.
 */
public interface IvyModuleDescriptor {
    /***
     * Returns the branch attribute of the info element in this descriptor.
     *
     * @return the branch for this descriptor, or null if no branch was declared in the descriptor.
     */
    @Nullable
    String getBranch();

    /**
     * Returns the status attribute of the info element in this descriptor.  Note that this <i>always</i> represents
     * the status from the ivy.xml for this module.  It is not affected by changes to the status made via
     * the {@link org.gradle.api.artifacts.ComponentMetadataDetails} interface in a component metadata rule.
     *
     * @return the status for this descriptor
     */
    String getIvyStatus();

    /**
     * Returns an {@link org.gradle.api.artifacts.ivy.IvyExtraInfo} representing the "extra" info declared
     * in this descriptor.
     * <p>
     * The extra info is the set of all non-standard subelements of the <em>info</em> element.
     *
     * @return an {@link org.gradle.api.artifacts.ivy.IvyExtraInfo} representing the extra info declared in this descriptor
     */
    IvyExtraInfo getExtraInfo();
}
