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
package org.gradle.nativeplatform.toolchain.internal.tools;

import org.gradle.nativeplatform.toolchain.internal.ToolType;

public class DefaultGccCommandLineToolConfiguration extends DefaultCommandLineToolConfiguration implements GccCommandLineToolConfigurationInternal {
    private String executable;

    public DefaultGccCommandLineToolConfiguration(ToolType toolType, String defaultExecutable) {
        super(toolType);
        this.executable = defaultExecutable;
    }

    @Override
    public String getExecutable() {
        return executable;
    }

    @Override
    public void setExecutable(String file) {
        executable = file;
    }
}
