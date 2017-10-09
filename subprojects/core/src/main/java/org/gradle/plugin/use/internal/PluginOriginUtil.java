/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugin.use.internal;

import org.apache.commons.lang.StringUtils;
import org.gradle.groovy.scripts.ScriptSource;

public final class PluginOriginUtil {

    private PluginOriginUtil() {}

    public static String scriptSourceDisplayName(ScriptSource scriptSource, Integer lineNumber) {
        if (scriptSource == null || scriptSource.getDisplayName() == null) {
            return null;
        }
        String sourceMsg = StringUtils.capitalize(scriptSource.getDisplayName());
        if (lineNumber == null) {
            return sourceMsg;
        }
        return String.format("%s line: %d", sourceMsg, lineNumber);
    }

    public static String autoAppliedPluginDisplayName() {
        return "auto-applied";
    }
}
