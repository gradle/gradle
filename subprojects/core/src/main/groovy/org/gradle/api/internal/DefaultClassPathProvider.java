/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal;

import java.util.List;
import java.util.regex.Pattern;

public class DefaultClassPathProvider extends AbstractClassPathProvider {
    public DefaultClassPathProvider() {
        List<Pattern> groovyPatterns = toPatterns("groovy-all");

        add("LOCAL_GROOVY", groovyPatterns);
        List<Pattern> gradleApiPatterns = toPatterns("gradle-\\w+", "ivy", "slf4j", "ant");
        gradleApiPatterns.addAll(groovyPatterns);
        // Add the test fixture runtime, too
        gradleApiPatterns.addAll(toPatterns("commons-io", "asm", "commons-lang", "commons-collections", "maven-ant-tasks"));
        add("GRADLE_API", gradleApiPatterns);
        add("GRADLE_CORE", toPatterns("gradle-core"));
        add("ANT", toPatterns("ant", "ant-launcher"));
        add("COMMONS_CLI", toPatterns("commons-cli"));
    }
}
