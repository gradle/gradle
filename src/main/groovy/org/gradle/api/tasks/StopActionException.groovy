/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.api.tasks

import org.gradle.api.GradleException

/**
 * An action can usually be stopped by just calling return inside the action closure.
 * But if the action works with helper methods that can lead to redundant code.
 * For example <blockquote><pre>
 *     List existentSourceDirs = HelperUtil.getExistentSourceDirs()
 *     if (!existentSourceDirs) {return}
 * </pre></blockquote>
 * If the getExistentSourceDirs throws a StopActionException the tasks does not need the if statement.
 * 
 * @author Hans Dockter
 */
class StopActionException extends GradleException {
    StopActionException() {
        super()
    }

    StopActionException(String message) {
        super(message)
    }
}
