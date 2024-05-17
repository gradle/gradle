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
package org.gradle.tooling.model.eclipse;

import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.UnsupportedMethodException;

/**
 * An element that can have classpath attributes.
 *
 * @since 2.14
 */
public interface EclipseClasspathEntry {

    /**
     * Returns the classpath attributes associated with this classpath entry.
     *
     * @return The classpath attributes.
     * @throws UnsupportedMethodException For Gradle versions older than 2.14, where this method is not supported.
     */
    DomainObjectSet<? extends ClasspathAttribute> getClasspathAttributes() throws UnsupportedMethodException;

    /**
     * Returns the access rules associated with this classpath entry.
     *
     * @return The access rules.
     * @throws UnsupportedMethodException For Gradle versions older than 3.0, where this method is not supported.
     *
     * @since 3.0
     */
    DomainObjectSet<? extends AccessRule> getAccessRules() throws UnsupportedMethodException;
}
