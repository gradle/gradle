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

import org.gradle.nativebinaries.toolchain.internal.ToolType;

import java.util.HashMap;
import java.util.Map;

public class DefaultToolRegistry implements ToolRegistry {
    private final Map<ToolType, GccToolInternal> gccTools = new HashMap<ToolType, GccToolInternal>();

    // TODO:DAZ Should not need default executable here?
    public void register(ToolType toolType, String defaultExecutable) {
        DefaultTool tool = new DefaultTool(toolType, defaultExecutable);
        gccTools.put(toolType, tool);
    }

    public GccToolInternal getTool(ToolType toolType) {
        return gccTools.get(toolType);
    }
}
