/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.daemon.server.api;

import org.gradle.launcher.daemon.protocol.Stop;
import org.gradle.launcher.daemon.protocol.StopWhenIdle;
import org.gradle.launcher.daemon.protocol.Success;

public class HandleStop implements DaemonCommandAction {
    @Override
    public void execute(DaemonCommandExecution execution) {
        if (execution.getCommand() instanceof Stop) {
            execution.getDaemonStateControl().requestForcefulStop();
            execution.getConnection().completed(new Success(null));
        } else if (execution.getCommand() instanceof StopWhenIdle) {
            execution.getDaemonStateControl().requestStop();
            execution.getConnection().completed(new Success(null));
        } else {
            execution.proceed();
        }
    }
}
