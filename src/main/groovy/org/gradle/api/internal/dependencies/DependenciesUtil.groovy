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

package org.gradle.api.internal.dependencies

import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import java.util.regex.Matcher
import org.gradle.api.InvalidUserDataException
import java.util.regex.Pattern

/**
 * @author Hans Dockter
 */
class DependenciesUtil {
    static final Pattern extensionSplitter = ~/(.+)\@([^:]+$)/

    static ModuleRevisionId moduleRevisionId(String org, String name, String revision, Map extraAttributes = [:]) {
        new ModuleRevisionId(new ModuleId(org, name), revision, extraAttributes)
    }

    static Map splitExtension(String s) {
        Matcher matcher = (s =~ extensionSplitter)
        boolean matches = matcher.matches()
        if (!matches || matcher.groupCount() != 2) { throw new InvalidUserDataException("The description $s is invalid") }
        [core: matcher.group(1), extension: matcher.group(2)]       
    }

    static boolean hasExtension(String s) {
        (s =~ extensionSplitter).matches()
    }
}
