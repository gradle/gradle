/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.nativeplatform.toolchain.internal.xcode;

import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

public abstract class AbstractLocator {
    private final ExecActionFactory execActionFactory;
    private File cachedLocation;

    protected AbstractLocator(ExecActionFactory execActionFactory) {
        this.execActionFactory = execActionFactory;
    }

    protected abstract List<String> getXcrunFlags();

    public File find() {
        synchronized (this) {
            if (cachedLocation == null) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ExecAction execAction = execActionFactory.newExecAction();
                execAction.executable("xcrun");
                execAction.workingDir(System.getProperty("user.dir"));
                execAction.args(getXcrunFlags());

                String developerDir = System.getenv("DEVELOPER_DIR");
                if (developerDir != null) {
                    execAction.environment("DEVELOPER_DIR", developerDir);
                }
                execAction.getStandardOutput().set(outputStream);
                execAction.execute().assertNormalExitValue();

                cachedLocation = new File(outputStream.toString().replace("\n", ""));
            }
            return cachedLocation;
        }
    }
}
