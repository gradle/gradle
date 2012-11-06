/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.groovy.scripts.internal;

import org.gradle.util.DeprecationLogger;

/**
 * Used by {@link StatementLabelsScriptTransformer} which weaves calls to the log() method into build scripts.
 */
public class StatementLabelsDeprecationLogger {
    public static void log(String label, String sample) {
        DeprecationLogger.nagUserOfDeprecated(
                "Usage of statement labels in build scripts",
                String.format(
                        "In case you tried to configure a property named '%s', replace ':' with '=' or ' '. Otherwise it will not have the desired effect. \n\n%s",
                        label, sample
                )
        );
    }
}
