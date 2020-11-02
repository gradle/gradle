/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jvm.inspection;

import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.JavaInfo;
import org.gradle.internal.jvm.Jvm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachingJvmVersionDetector implements JvmVersionDetector {
    private final Map<JavaInfo, JavaVersion> javaHomeResults = new ConcurrentHashMap<JavaInfo, JavaVersion>();
    private final Map<String, JavaVersion> javaCmdResults = new ConcurrentHashMap<String, JavaVersion>();
    private final JvmVersionDetector delegate;

    public CachingJvmVersionDetector(JvmVersionDetector delegate) {
        this.delegate = delegate;
        javaHomeResults.put(Jvm.current(), JavaVersion.current());
        javaCmdResults.put(Jvm.current().getJavaExecutable().getPath(), JavaVersion.current());
    }

    @Override
    public JavaVersion getJavaVersion(JavaInfo jvm) {
        JavaVersion version = javaHomeResults.get(jvm);
        if (version != null) {
            return version;
        }

        version = delegate.getJavaVersion(jvm);
        javaHomeResults.put(jvm, version);

        return version;
    }

    @Override
    public JavaVersion getJavaVersion(String javaCommand) {
        JavaVersion version = javaCmdResults.get(javaCommand);
        if (version != null) {
            return version;
        }

        version = delegate.getJavaVersion(javaCommand);
        javaCmdResults.put(javaCommand, version);
        return version;
    }
}
