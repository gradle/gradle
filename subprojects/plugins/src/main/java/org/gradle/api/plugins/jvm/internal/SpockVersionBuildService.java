/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import groovy.lang.GroovySystem;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A build service that can scan a configuration and determine the Spock version present.
 *
 * @since 8.0
 */
public abstract class SpockVersionBuildService implements BuildService<SpockVersionBuildService.Params>, AutoCloseable {
    public interface Params extends BuildServiceParameters { /* unused */ }

    public static final String NAME = "spockVersion";

    private static final Set<String> GROOVY_GROUPS = ImmutableSet.of("org.codehaus.groovy", "org.apache.groovy");
    private static final Set<String> GROOVY_NAMES = ImmutableSet.of("groovy", "groovy-all");
    private static final Map<VersionNumber, String> GROOVY_TO_SPOCK_VERSION_MAPPING = ImmutableMap.of(
            VersionNumber.parse("4.0"), "2.2-groovy-4.0",
            VersionNumber.parse("3.0"), "2.2-groovy-3.0",
            VersionNumber.parse("2.5"), "2.2-groovy-2.5",
            VersionNumber.parse("2.4"), "1.3-groovy-2.4",
            VersionNumber.parse("2.3"), "1.1-groovy-2.3",
            VersionNumber.parse("2.0"), "1.1-groovy-2.0",
            VersionNumber.parse("1.8"), "0.7-groovy-1.8",
            VersionNumber.parse("1.7"), "0.6-groovy-1.7",
            VersionNumber.parse("1.6"), "0.5-groovy-1.6"
    );

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    public Provider<String> getDerivedSpockVersion(Configuration runtimeConf) {
        return getProviderFactory().provider(() -> {
            Optional<VersionNumber> maybeGroovyJarVersionNumber = runtimeConf.getIncoming().getResolutionResult().getAllComponents().stream()
                    .map(ResolvedComponentResult::getModuleVersion)
                    .filter(mvi -> GROOVY_GROUPS.contains(mvi.getGroup()))
                    .filter(mvi -> GROOVY_NAMES.contains(mvi.getName()))
                    .map(ModuleVersionIdentifier::getVersion)
                    .map(VersionNumber::parse)
                    .findFirst();

            // TODO: Should we warn if we can't find Groovy on their classpath?
            VersionNumber groovyVersion = maybeGroovyJarVersionNumber.orElse(VersionNumber.parse(GroovySystem.getVersion()));

            return getSpockVersionForGroovy(groovyVersion);
        });
    }

    private String getSpockVersionForGroovy(VersionNumber groovyVersion) {
        return GROOVY_TO_SPOCK_VERSION_MAPPING.entrySet().stream()
                .filter(it -> groovyVersion.compareTo(it.getKey()) >= 0)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(DefaultJvmTestSuite.TestingFramework.SPOCK.getDefaultVersion());
    }

    @Override
    public void close() { /* unused */ }
}
