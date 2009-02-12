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

package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.InvalidUserDataException;
import org.gradle.util.WrapUtil;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hans Dockter
 */
public class DependenciesUtil {
    public static final Pattern extensionSplitter = Pattern.compile("^(.+)\\@([^:]+$)");
    public static final String CORE = "core";

    public static Map<String, String> splitExtension(String s) {
        Matcher matcher = extensionSplitter.matcher(s);
        boolean matches = matcher.matches();
        if (!matches || matcher.groupCount() != 2) {
            throw new InvalidUserDataException("The description " + s + " is invalid");
        }
        Map map = WrapUtil.toMap(CORE, matcher.group(1));
        map.put("extension", matcher.group(2));
        return map;
    }

    public static boolean hasExtension(String s) {
        return extensionSplitter.matcher(s).matches();
    }
}
