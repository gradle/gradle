/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.tooling.model.idea;

/**
 * IdeaProject that does not provide/resolve any external dependencies.
 * Only project dependencies and local file dependencies are included on the modules' classpath.
 * <p>
 * Useful for 'previewing' the output model of IdeaProject because it is supposed to be fast (e.g. does not download dependencies from the web).
 *
 * @since 1.0-milestone-5
 */
public interface BasicIdeaProject extends IdeaProject {
}
