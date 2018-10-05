/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.launcher.cli;

import org.gradle.util.GUtil;

import java.net.URL;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Allows fetching the current Groovy version without initializing the {@link groovy.lang.GroovySystem}.
 * The latter currently causes illegal access warnings that are very annoying to our users.
 */
public class GroovyVersion {
    static String current() {
        URL resource = GroovyVersion.class.getClassLoader().getResource("META-INF/groovy-release-info.properties");
        checkNotNull(resource, "Groovy version manifest was not found");
        Properties versions = GUtil.loadProperties(resource);
        return versions.getProperty("ImplementationVersion");
    }
}
