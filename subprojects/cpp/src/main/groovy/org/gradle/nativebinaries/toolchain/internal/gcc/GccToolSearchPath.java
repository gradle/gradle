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

package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.toolchain.internal.tools.ToolSearchPath;

import java.io.File;
import java.util.Arrays;
import java.util.List;

class GccToolSearchPath extends ToolSearchPath {
    public GccToolSearchPath(OperatingSystem operatingSystem) {
        super(operatingSystem);
    }

    @Override
    protected File findExecutable(OperatingSystem operatingSystem, String name) {
        List<String> candidates;
        if (operatingSystem.isWindows()) {
            // Under Cygwin, g++/gcc is a Cygwin symlink to either g++-3 or g++-4. We can't run g++ directly
            candidates = Arrays.asList(name + "-4", name + "-3", name);
        } else {
            candidates = Arrays.asList(name);
        }
        for (String candidate : candidates) {
            File executable = super.findExecutable(operatingSystem, candidate);
            if (executable != null) {
                return executable;
            }
        }
        return null;
    }

}
