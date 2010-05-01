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

import org.gradle.api.GradleException;

/**
 * @author Hans Dockter
 */
public class ExecException extends GradleException {
    private ExecResult execResult;

    public ExecException(ExecResult execResult) {
        super();    //To change body of overridden methods use File | Settings | File Templates.
        this.execResult = execResult;
    }

    public ExecException(String message, ExecResult execResult) {
        super(message);    //To change body of overridden methods use File | Settings | File Templates.
        this.execResult = execResult;
    }

    public ExecResult getExecResult() {
        return execResult;
    }
}
