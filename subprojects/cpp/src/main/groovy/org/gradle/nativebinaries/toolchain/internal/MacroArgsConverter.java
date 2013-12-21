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

package org.gradle.nativebinaries.toolchain.internal;

import org.gradle.api.Transformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MacroArgsConverter implements Transformer<List<String>, Map<String, String>> {
    public List<String> transform(Map<String, String> original) {
        List<String> macroList = new ArrayList<String>(original.size());
        for (String macroName : original.keySet()) {
            String macroDef = original.get(macroName);
            String arg = macroDef == null ? macroName : String.format("%s=%s", macroName, macroDef);
            macroList.add(arg);
        }
        return macroList;
    }
}
