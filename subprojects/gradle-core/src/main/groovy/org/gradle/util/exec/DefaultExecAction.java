/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util.exec;

/**
 * @author Hans Dockter
 */
public class DefaultExecAction extends ExecHandleBuilder implements ExecAction {
    public ExecResult execute() {
        ExecHandle execHandle = build();
        execHandle.startAndWaitForFinish();
        ExecResult execResult = createExecResult(execHandle);
        if (!isIgnoreExitValue() && execResult.getExitValue() != 0) {
            throw new ExecException("Process finished with non zero exit value.", execResult);
        }
        return execResult;
    }

    ExecResult createExecResult(ExecHandle execHandle) {
        final int exitValue = execHandle.getExitCode();
        return new ExecResult() {
            public int getExitValue() {
                return exitValue;
            }
        };
    }
}
