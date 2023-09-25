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
 * Access rule associated with an Eclipse classpath entry.
 *
 * @see <a href="http://help.eclipse.org/mars/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/IAccessRule.html">IAccessRule Javadoc</a>
 *
 * @since 3.0
 */
public interface AccessRule {

    /**
     * Returns the encoded type of the access rule. The meaning of the values:
     * <ul>
     *     <li>0: the rule defines accessible paths</li>
     *     <li>1: the rule defines inaccessible paths</li>
     *     <li>2: the rule defines discouraged paths</li>
     * </ul>
     *
     * @return The type of this access rule.
     */
    int getKind();

    /**
     * Returns the file pattern of this access rule.
     *
     * @return The file pattern of this access rule.
     */
    String getPattern();
}
