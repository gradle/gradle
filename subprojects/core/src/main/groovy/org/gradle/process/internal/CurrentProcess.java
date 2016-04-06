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

package org.gradle.process.internal;

import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;

import java.lang.management.ManagementFactory;

public class CurrentProcess {
    private final JavaInfo jvm;
    private final JvmOptions effectiveJvmOptions;

    public CurrentProcess() {
        this(Jvm.current(), inferJvmOptions());
    }

    protected CurrentProcess(JavaInfo jvm, JvmOptions effectiveJvmOptions) {
        this.jvm = jvm;
        this.effectiveJvmOptions = effectiveJvmOptions;
    }

    public JvmOptions getJvmOptions() {
        return effectiveJvmOptions;
    }

    public JavaInfo getJvm() {
        return jvm;
    }

    private static JvmOptions inferJvmOptions() {
        // Try to infer the effective jvm options for the currently running process.
        // We only care about 'managed' jvm args, anything else is unimportant to the running build
        JvmOptions jvmOptions = new JvmOptions(new IdentityFileResolver());
        jvmOptions.setAllJvmArgs(ManagementFactory.getRuntimeMXBean().getInputArguments());
        return jvmOptions;
    }
}
