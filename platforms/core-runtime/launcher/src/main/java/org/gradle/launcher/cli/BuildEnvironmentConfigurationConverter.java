/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.launcher.configuration.AllProperties;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.configuration.BuildLayoutResult;
import org.gradle.launcher.cli.converter.BuildOptionBackedConverter;
import org.gradle.launcher.configuration.InitialProperties;
import org.gradle.launcher.cli.converter.InitialPropertiesConverter;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.cli.converter.StartParameterConverter;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.launcher.daemon.configuration.DaemonJvmToolchainCriteriaOptions;
import org.gradle.launcher.daemon.configuration.DaemonJvmToolchainSpec;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

import javax.annotation.Nullable;
import java.io.File;

public class BuildEnvironmentConfigurationConverter {

    private final InitialPropertiesConverter initialPropertiesConverter;
    private final BuildLayoutConverter buildLayoutConverter;
    private final LayoutToPropertiesConverter layoutToPropertiesConverter;
    private final StartParameterConverter startParameterConverter;
    private final BuildOptionBackedConverter<DaemonParameters> daemonParametersConverter;
    private final BuildOptionBackedConverter<DaemonJvmToolchainSpec> daemonJvmToolchainCriteriaConverter;
    private final FileCollectionFactory fileCollectionFactory;
    private final PropertyFactory propertyFactory;

    BuildEnvironmentConfigurationConverter(InitialPropertiesConverter initialPropertiesConverter,
                                           BuildLayoutConverter buildLayoutConverter,
                                           LayoutToPropertiesConverter layoutToPropertiesConverter,
                                           StartParameterConverter startParameterConverter,
                                           BuildOptionBackedConverter<DaemonParameters> daemonParametersConverter,
                                           BuildOptionBackedConverter<DaemonJvmToolchainSpec> daemonJvmToolchainCriteriaConverter,
                                           FileCollectionFactory fileCollectionFactory,
                                           PropertyFactory propertyFactory) {
        this.initialPropertiesConverter = initialPropertiesConverter;
        this.buildLayoutConverter = buildLayoutConverter;
        this.layoutToPropertiesConverter = layoutToPropertiesConverter;
        this.startParameterConverter = startParameterConverter;
        this.daemonParametersConverter = daemonParametersConverter;
        this.daemonJvmToolchainCriteriaConverter = daemonJvmToolchainCriteriaConverter;
        this.fileCollectionFactory = fileCollectionFactory;
        this.propertyFactory = propertyFactory;
    }

    public BuildEnvironmentConfigurationConverter(BuildLayoutFactory buildLayoutFactory, FileCollectionFactory fileCollectionFactory, PropertyFactory propertyFactory) {
        this(new InitialPropertiesConverter(),
            new BuildLayoutConverter(),
            new LayoutToPropertiesConverter(buildLayoutFactory),
            new StartParameterConverter(),
            new BuildOptionBackedConverter<>(new DaemonBuildOptions()),
            new BuildOptionBackedConverter<>(new DaemonJvmToolchainCriteriaOptions()),
            fileCollectionFactory,
            propertyFactory);
    }

    public Parameters convertParameters(ParsedCommandLine args, @Nullable File currentDir) throws CommandLineArgumentException {
        InitialProperties initialProperties = initialPropertiesConverter.convert(args);
        BuildLayoutResult buildLayout = buildLayoutConverter.convert(initialProperties, args, currentDir);
        AllProperties properties = layoutToPropertiesConverter.convert(initialProperties, buildLayout);
        StartParameterInternal startParameter = new StartParameterInternal();
        startParameterConverter.convert(args, buildLayout, properties, startParameter);

        DaemonParameters daemonParameters = new DaemonParameters(buildLayout, fileCollectionFactory, properties.getRequestedSystemProperties());
        daemonParametersConverter.convert(args, properties.getProperties(), daemonParameters);

        return new Parameters(startParameter, daemonParameters, properties);
    }

    @Nullable
    public DaemonJvmToolchainSpec convertJvmToolchainCriteria(ParsedCommandLine args, AllProperties properties) {
        DaemonJvmToolchainSpec jvmToolchainCriteria = new DaemonJvmToolchainSpec(propertyFactory);
        daemonJvmToolchainCriteriaConverter.convert(args, properties.getBuildProperties(), jvmToolchainCriteria);
        return jvmToolchainCriteria.getLanguageVersion().isPresent() ? jvmToolchainCriteria : null;
    }

    public void configure(CommandLineParser parser) {
        initialPropertiesConverter.configure(parser);
        buildLayoutConverter.configure(parser);
        startParameterConverter.configure(parser);
        daemonParametersConverter.configure(parser);
        daemonJvmToolchainCriteriaConverter.configure(parser);
    }
}
