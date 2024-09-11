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

package org.gradle.initialization;

import com.google.common.collect.ImmutableSet;
import org.apache.groovy.json.DefaultFastStringServiceFactory;
import org.apache.groovy.json.FastStringServiceFactory;

import java.util.Set;

public class DefaultGradleApiSpecProvider extends GradleApiSpecProvider.SpecAdapter implements GradleApiSpecProvider {

    @Override
    public Set<Class<?>> getExportedClasses() {
        return ImmutableSet.<Class<?>>of(
            FastStringServiceFactory.class,
            DefaultFastStringServiceFactory.class
        );
    }

    @Override
    public Set<String> getExportedPackages() {
        return ImmutableSet.of(
            //"org.gradle.internal.declarativedsl", // TODO: adding this makes all integration tests fail
            "org.gradle",
            "org.apache.tools.ant",
            "groovy",
            "org.apache.groovy",
            "org.codehaus.groovy",
            "groovyjarjarantlr",
            "org.slf4j",
            "org.apache.commons.logging",
            "org.apache.log4j",
            "javax.annotation",
            "javax.inject");
    }

    @Override
    public Set<String> getUnexportedPackages() {
        return ImmutableSet.of(
            // This package is not exported to Gradle API default classloader,
            // and can be used for worker action code that needs to access external libraries.
            // See also explanation in https://github.com/gradle/gradle/pull/29591#issuecomment-2216917657.
            "org.gradle.unexported"
        );
    }

    @Override
    public Set<String> getExportedResourcePrefixes() {
        return ImmutableSet.of(
            "META-INF/gradle-plugins"
        );
    }

    @Override
    public Set<String> getExportedResources() {
        return ImmutableSet.of(
            "META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule",
            "META-INF/services/org.apache.groovy.json.FastStringServiceFactory"
        );
    }

    @Override
    public Spec get() {
        return this;
    }
}
