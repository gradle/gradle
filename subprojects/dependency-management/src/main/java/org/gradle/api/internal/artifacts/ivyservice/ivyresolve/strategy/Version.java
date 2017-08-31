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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

/**
 * A parsed version.
 *
 * This should be synced with {@link org.gradle.util.VersionNumber} and {@link org.gradle.util.GradleVersion} at some point.
 */
public interface Version {
    /**
     * Returns the original {@link String} representation of the version.
     */
    String getSource();

    /**
     * Returns all the parts of this version. e.g. 1.2.3 returns [1,2,3] or 1.2-beta4 returns [1,2,beta,4].
     */
    String[] getParts();

    /**
     * Returns all the numeric parts of this version as {@link Long}, with nulls in non-numeric positions. eg. 1.2.3 returns [1,2,3] or 1.2-beta4 returns [1,2,null,4].
     */
    Long[] getNumericParts();

    /**
     * Returns the base version for this version, which removes any qualifiers. Generally this is the first '.' separated parts of this version.
     * e.g. 1.2.3-beta-4 returns 1.2.3, or 7.0.12beta5 returns 7.0.12.
     */
    Version getBaseVersion();

    /**
     * Returns true if this version is qualified in any way. For example, 1.2.3 is not qualified, 1.2-beta-3 is.
     */
    boolean isQualified();
}
