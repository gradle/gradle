/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks.util;

import groovy.util.AntBuilder;

import java.io.File;
import java.util.Set;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class CopyInstructionFactory {
    private AntBuilder antBuilder;

    public CopyInstructionFactory(AntBuilder antBuilder) {
        this.antBuilder = antBuilder;
    }

    public CopyInstruction createCopyInstruction(File sourceDir, File targetDir, Set includes, Set excludes, Map filters) {
        CopyInstruction copyInstruction = new CopyInstruction();
        copyInstruction.setSourceDir(sourceDir);
        copyInstruction.setTargetDir(targetDir);
        copyInstruction.setIncludes(includes);
        copyInstruction.setExcludes(excludes);
        copyInstruction.setFilters(filters);
        copyInstruction.setAntBuilder(antBuilder);
        return copyInstruction;
    }

    public AntBuilder getAntBuilder() {
        return antBuilder;
    }
}
