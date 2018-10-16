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

/**
 * Optional description associated with Eclipse classpath entries.
 *
 * @see <a href="http://help.eclipse.org/mars/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/IClasspathAttribute.html">IClassAttribute Javadoc</a>
 *
 * @since 2.14
 */
public interface ClasspathAttribute {

    /**
     * Returns the key of this attribute.
     *
     * @return The key of this attribute..
     */
    String getName();

    /**
     * Returns the value of this attribute.
     *
     * @return The value of this classpath attribute.
     */
    String getValue();

}
