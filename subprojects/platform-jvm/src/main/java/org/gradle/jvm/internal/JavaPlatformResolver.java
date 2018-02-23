/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.jvm.internal;

import com.google.common.collect.Lists;
import org.gradle.api.JavaVersion;
import org.gradle.jvm.platform.JavaPlatform;
import org.gradle.jvm.platform.internal.DefaultJavaPlatform;
import org.gradle.platform.base.internal.PlatformRequirement;
import org.gradle.platform.base.internal.PlatformResolver;

import java.util.List;

public class JavaPlatformResolver implements PlatformResolver<JavaPlatform> {
    private final List<JavaPlatform> platforms = Lists.newArrayList();

    public JavaPlatformResolver() {
        for (JavaVersion javaVersion : JavaVersion.values()) {
            DefaultJavaPlatform javaPlatform = new DefaultJavaPlatform(javaVersion);
            platforms.add(javaPlatform);
        }
    }

    @Override
    public Class<JavaPlatform> getType() {
        return JavaPlatform.class;
    }

    @Override
    public JavaPlatform resolve(PlatformRequirement platformRequirement) {
        for (JavaPlatform platform : platforms) {
            if (platform.getName().equals(platformRequirement.getPlatformName())) {
                return platform;
            }
        }
        return null;
    }
}
