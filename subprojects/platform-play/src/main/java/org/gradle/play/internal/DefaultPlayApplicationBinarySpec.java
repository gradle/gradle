/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal;

import org.gradle.platform.base.binary.BaseBinarySpec;
import org.gradle.play.platform.PlayPlatform;
import org.gradle.play.internal.toolchain.PlayToolChainInternal;

import java.io.File;

public class DefaultPlayApplicationBinarySpec extends BaseBinarySpec implements PlayApplicationBinarySpecInternal {
    private PlayPlatform platform;
    private PlayToolChainInternal toolChain;
    private File jarFile;

    public PlayPlatform getTargetPlatform() {
        return platform;
    }

    public PlayToolChainInternal getToolChain() {
        return toolChain;
    }

    public File getJarFile() {
        return jarFile;
    }

    public void setTargetPlatform(PlayPlatform platform) {
        this.platform = platform;
    }

    public void setToolChain(PlayToolChainInternal toolChain) {
        this.toolChain = toolChain;
    }

    public void setJarFile(File file) {
        this.jarFile = file;
    }
}
