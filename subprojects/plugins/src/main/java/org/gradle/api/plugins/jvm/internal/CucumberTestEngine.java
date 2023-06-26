/*
 * Copyright 2023 the original author or authors.
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
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.jvm.TestEngine;
import org.gradle.api.plugins.jvm.TestEngineConfigurationParameters;
import org.gradle.api.plugins.jvm.TestEngineRegistration;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.SystemPropertiesCommandLineProvider;

import javax.inject.Inject;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

/**
 * A custom test engine class providing support for the Cucumber test engine.
 */
abstract public class CucumberTestEngine implements TestEngine<CucumberTestEngine.CucumberTestEngineRegistration> {
    /**
     * The registration for the Cucumber test engine exposing a single version property.
     */
    interface CucumberTestEngineRegistration extends TestEngineRegistration {
        Property<String> getVersion();
    }

    /**
     * The configuration parameters for the Cucumber test engine exposing two parameters.
     */
    abstract static class CucumberTestEngineConfigurationParameters implements TestEngineConfigurationParameters {
        @Input
        abstract Property<String> getNamingStrategy();
        @InputFiles
        abstract FileCollection getFeatureFiles();

        @Override
        public CommandLineArgumentProvider mapToConfigurationParameters() {
            // SystemPropertiesCommandLineProvider is a convenience class that provides only system properties from a map.
            // It exposes the supplied annotationProvider object as a nested input/output property for proper up-to-date checking.
            return new SystemPropertiesCommandLineProvider<CucumberTestEngineConfigurationParameters>(this) {
                @Override
                public Map<String, String> getSystemProperties() {
                    return new ImmutableMap.Builder<String, String>()
                        .put("cucumber.junit-platform.naming-strategy", getNamingStrategy().get())
                        .put("cucumber.features", String.join(",", Sets.newTreeSet(getFeatureFiles().getFiles().stream().map(f -> f.getAbsolutePath()).collect(java.util.stream.Collectors.toList()))))
                        .build();
                }
            };
        }
    }

    @Override
    public Class<? extends TestEngineConfigurationParameters> getConfigurationParametersType() {
        return CucumberTestEngineConfigurationParameters.class;
    }

    @Inject
    public CucumberTestEngine() {
    }

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return singletonList(getDependencyFactory().create("io.cucumber:cucumber-junit-platform-engine:" + getRegistration().getVersion().get()));
    }

    @Override
    public Iterable<Dependency> getCompileOnlyDependencies() {
        return emptyList();
    }

    @Override
    public Iterable<Dependency> getRuntimeOnlyDependencies() {
        return emptyList();
    }
}
