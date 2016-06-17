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

package org.gradle.launcher.daemon.server.api;

import org.gradle.launcher.daemon.protocol.ReportStatus;
import org.gradle.launcher.daemon.protocol.Success;
import org.gradle.util.GradleVersion;

public class HandleReportStatus implements DaemonCommandAction {
    @Override
    public void execute(DaemonCommandExecution execution) {
        if (execution.getCommand() instanceof ReportStatus) {
            String pid = execution.getDaemonContext().getPid().toString();
            String version = GradleVersion.current().getBaseVersion().getVersion();
            String status = execution.getDaemonStateControl().isIdle() ? "IDLE" : "BUSY";
            // TODO(ew): consider returning a POJO instead of a string to keep formatting in 1 place
            String message = String.format("%1$6s %2$-7s %3$s", pid, version, status);
            execution.getConnection().completed(new Success(message));
        } else {
            execution.proceed();
        }
    }
}
