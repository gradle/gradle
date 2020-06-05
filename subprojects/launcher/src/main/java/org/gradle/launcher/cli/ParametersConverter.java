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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.cli.CommandLineConverter;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.launcher.cli.converter.BuildLayoutConverter;
import org.gradle.launcher.cli.converter.StartParameterConverter;
import org.gradle.launcher.cli.converter.LayoutToPropertiesConverter;
import org.gradle.launcher.cli.converter.PropertiesToDaemonParametersConverter;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.launcher.daemon.configuration.DaemonParameters;

public class ParametersConverter {

    private final BuildLayoutConverter buildLayoutConverter;

    private final LayoutToPropertiesConverter layoutToPropertiesConverter;

    private final StartParameterConverter startParameterConverter;

    private final CommandLineConverter<DaemonParameters> daemonConverter;
    private final PropertiesToDaemonParametersConverter propertiesToDaemonParametersConverter;
    private final FileCollectionFactory fileCollectionFactory;

    ParametersConverter(BuildLayoutConverter buildLayoutConverter,
                        LayoutToPropertiesConverter layoutToPropertiesConverter,
                        StartParameterConverter startParameterConverter,
                        CommandLineConverter<DaemonParameters> daemonConverter,
                        PropertiesToDaemonParametersConverter propertiesToDaemonParametersConverter,
                        FileCollectionFactory fileCollectionFactory) {
        this.buildLayoutConverter = buildLayoutConverter;
        this.layoutToPropertiesConverter = layoutToPropertiesConverter;
        this.startParameterConverter = startParameterConverter;
        this.daemonConverter = daemonConverter;
        this.propertiesToDaemonParametersConverter = propertiesToDaemonParametersConverter;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    public ParametersConverter(BuildLayoutFactory buildLayoutFactory, FileCollectionFactory fileCollectionFactory) {
        this(new BuildLayoutConverter(),
            new LayoutToPropertiesConverter(buildLayoutFactory),
            new StartParameterConverter(),
            new DaemonBuildOptions().commandLineConverter(),
            new PropertiesToDaemonParametersConverter(),
            fileCollectionFactory);
    }

    public Parameters convert(ParsedCommandLine args, Parameters target) throws CommandLineArgumentException {
        BuildLayoutConverter.Result buildLayout = buildLayoutConverter.convert(args);
        LayoutToPropertiesConverter.Result properties = layoutToPropertiesConverter.convert(buildLayout);

        buildLayout.applyTo(target.getLayout());
        startParameterConverter.convert(args, buildLayout, properties, target.getStartParameter());

        DaemonParameters daemonParameters = new DaemonParameters(target.getLayout(), fileCollectionFactory, target.getStartParameter().getSystemPropertiesArgs());
        propertiesToDaemonParametersConverter.convert(properties.getProperties(), daemonParameters);
        daemonConverter.convert(args, daemonParameters);
        target.setDaemonParameters(daemonParameters);

        return target;
    }

    public void configure(CommandLineParser parser) {
        buildLayoutConverter.configure(parser);
        startParameterConverter.configure(parser);
        daemonConverter.configure(parser);
    }
}
