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

package org.gradle.nativeplatform.tasks;

import java.util.LinkedHashMap;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.nativeplatform.toolchain.internal.PCHUtils;

/**
 * Generates a prefix header file from a list of macros to be precompiled.
 * 
 * @since 5.1
 */
@Incubating
public class PrefixHeaderFileMacroGenerateTask extends DefaultTask {
    
    private final Map<String, String> macros = new LinkedHashMap<String, String>();
    private final RegularFileProperty prefixHeaderFile;
    
    public PrefixHeaderFileMacroGenerateTask() {
        this.prefixHeaderFile = getProject().getObjects().fileProperty();
    }
 
    @TaskAction
    void generatePrefixHeaderFile() {
        PCHUtils.generatePrefixHeaderMacroFile(macros, prefixHeaderFile.getAsFile().get());
    }

    /**
     * get macros
     * 
     * @return provider list of strings
     * @since 5.1
     */
    @Input
    public Map<String, String> getMacros() {
        return macros;
    }

    public void setMacros(Map<String, String> macros) {
        this.macros.clear();
        this.macros.putAll(macros);
    }
 
    /**
     * get prefix header output file
     * 
     * @return RegularFileProperty prefix header output fils
     * @since 5.1
     */
    @OutputFile
    public RegularFileProperty getPrefixHeaderFile() {
        return prefixHeaderFile;
    }
}
