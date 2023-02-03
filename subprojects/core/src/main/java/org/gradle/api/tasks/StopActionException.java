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

package org.gradle.api.tasks;

import org.gradle.api.GradleException;

/**
 * <p>A <code>StopActionException</code> is be thrown by a task {@link org.gradle.api.Action} or task action closure to
 * stop its own execution and to start execution of the task's next action. An action can usually be stopped by just
 * calling return inside the action closure. But if the action works with helper methods that can lead to redundant
 * code. For example:</p>
 *
 * <pre>
 *     List existentSourceDirs = HelperUtil.getExistentSourceDirs()
 *     if (!existentSourceDirs) {return}
 * </pre>
 * <p>If the <code>getExistentSourceDirs()</code> throws a <code>StopActionException</code> instead, the tasks does not
 * need the if statement.</p>
 *
 * <p>Note that throwing this exception does not fail the execution of the task or the build.</p>
 */
public class StopActionException extends GradleException {
    public StopActionException() {
        super();
    }

    public StopActionException(String message) {
        super(message);
    }
}
