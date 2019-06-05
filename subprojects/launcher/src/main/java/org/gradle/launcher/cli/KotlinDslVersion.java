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

final class KotlinDslVersion {

    static KotlinDslVersion current() {
        ClassLoader loader = KotlinDslVersion.class.getClassLoader();
        URL resource = loader.getResource("gradle-kotlin-dsl-versions.properties");
        checkNotNull(resource, "Gradle Kotlin DSL versions manifest was not found");
        Properties versions = GUtil.loadProperties(resource);
        return new KotlinDslVersion(versions.getProperty("kotlin"));
    }

    private final String kotlin;

    private KotlinDslVersion(String kotlin) {
        checkNotNull(kotlin, "Kotlin version was not found");
        this.kotlin = kotlin;
    }

    String getKotlinVersion() {
        return kotlin;
    }
}
