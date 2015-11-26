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

package org.gradle.nativeplatform;

import org.gradle.api.Incubating;
import org.gradle.model.internal.core.UnmanagedStruct;
import org.gradle.platform.base.ToolChain;

import java.io.File;

/**
 * Specifies how to build and where to place a native executable file.
 *
 * <p>TODO:HH resolve naming conflict with existing NativeExecutableSpec</p>
 */
@Incubating @UnmanagedStruct
public class NativeExecutableFileSpec {

    private File file;
    private ToolChain toolChain;

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public ToolChain getToolChain() {
        return toolChain;
    }

    public void setToolChain(ToolChain toolChain) {
        this.toolChain = toolChain;
    }
}
