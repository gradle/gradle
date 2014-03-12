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
package org.gradle.nativebinaries.toolchain.internal.tools;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.nativebinaries.toolchain.internal.ToolType;

import java.util.List;

public class PlatformGccTool implements GccToolInternal {
    private final GccToolInternal baseTool;
    private final List<String> platformArgs;

    public PlatformGccTool(GccToolInternal baseTool, List<String> platformArgs) {
        this.baseTool = baseTool;
        this.platformArgs = platformArgs;
    }

    public ToolType getToolType() {
        return baseTool.getToolType();
    }

    public String getExecutable() {
        return baseTool.getExecutable();
    }

    public void setExecutable(String file) {
        throw new UnsupportedOperationException();
    }

    public Action<List<String>> getArgAction() {
        Action<? super List<String>> platformArgsAction = new Action<List<String>>() {
            public void execute(List<String> strings) {
                strings.addAll(0, platformArgs);
            }
        };
        return Actions.composite(baseTool.getArgAction(), platformArgsAction);
    }

    public void withArguments(Closure arguments) {
        throw new UnsupportedOperationException();
    }
}
