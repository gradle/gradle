/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.artifacts.dsl;

/**
 * This class is for creating dependencies and adding them to configurations. The dependencies
 * that should be created can be described in a String or Map notation.
 *
 * <p>To create and add a specific dependency to a configuration you can use the following syntax:</p>
 *
 * <code>&lt;DependencyHandler>.&lt;configurationName> &lt;dependencyNotation1>, &lt;dependencyNotation2>, ...</code>
 *
 * <p>There are two allowed dependency notations. One is a string notation:</p>
 *
 * <code>"&lt;group>:&lt;name>:&lt;version>:&lt;classifier>"</code>
 *
 * <p>The other is a map notation:</p>
 * <code>group: &lt;group>, name: &lt;name>, version: &lt;version>, classifier: &lt;classifier></code>
 * 
 * <p>In both notations, all properties, except name, are optional.</p>
 *
 * @author Hans Dockter
 */
public interface DependencyHandler {
}
