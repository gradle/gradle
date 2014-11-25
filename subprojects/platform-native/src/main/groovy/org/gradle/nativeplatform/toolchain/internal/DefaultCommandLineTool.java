/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal;

import com.google.common.base.Joiner;
import org.gradle.api.GradleException;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecException;
import org.gradle.util.GFileUtils;

import java.io.File;

public class DefaultCommandLineTool implements CommandLineTool {
    private final String action;
    private final File executable;
    private final ExecActionFactory execActionFactory;

    public DefaultCommandLineTool(String action, File executable, ExecActionFactory execActionFactory) {
        this.action = action;
        this.executable = executable;
        this.execActionFactory = execActionFactory;
    }

    public void execute(CommandLineToolInvocation invocation) {
        ExecAction toolExec = execActionFactory.newExecAction();
        toolExec.executable(executable);
        if (invocation.getWorkDirectory() != null) {
            GFileUtils.mkdirs(invocation.getWorkDirectory());
            toolExec.workingDir(invocation.getWorkDirectory());
        }

        toolExec.args(invocation.getArgs());

        if (!invocation.getPath().isEmpty()) {
            String pathVar = OperatingSystem.current().getPathVar();
            String toolPath = Joiner.on(File.pathSeparator).join(invocation.getPath());
            toolPath = toolPath + File.pathSeparator + System.getenv(pathVar);
            toolExec.environment(pathVar, toolPath);
        }

        toolExec.environment(invocation.getEnvironment());

        try {
            toolExec.execute();
        } catch (ExecException e) {
            throw new GradleException(String.format("%s failed; see the error output for details.", action), e);
        }
    }
}
