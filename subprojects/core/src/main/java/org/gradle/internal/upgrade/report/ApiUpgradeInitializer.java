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

package org.gradle.internal.upgrade.report;

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.internal.metaobject.BeanDynamicObject.BeanDynamicObjectCallListenerRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.upgrade.report.config.ApiUpgradeConfig;
import org.gradle.internal.upgrade.report.config.ApiUpgradeConfigParser;
import org.gradle.internal.upgrade.report.config.NoopApiUpgradeConfig;
import org.gradle.internal.upgrade.report.groovy.DefaultSetPropertyApiUpgradeCallListener;
import org.gradle.internal.upgrade.report.groovy.CallsiteApiUpgradeDecoratorRegistry;
import org.gradle.internal.upgrade.report.groovy.DefaultCallsiteApiUpgradeDecorator;
import org.gradle.internal.upgrade.report.groovy.NoopSetPropertyApiUpgradeCallListener;
import org.gradle.internal.upgrade.report.groovy.NoopCallsiteApiUpgradeDecorator;
import org.gradle.internal.upgrade.report.problems.DefaultApiUpgradeProblemCollector;
import org.gradle.internal.upgrade.report.problems.NoopApiUpgradeProblemCollector;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

@ServiceScope(Scopes.BuildSession.class)
public class ApiUpgradeInitializer {

    private static final String BINARY_UPGRADE_JSON_PATH = "org.gradle.binary.upgrade.report.json";

    private final StartParameter startParameter;
    private final ApiUpgradeServiceProvider apiUpgradeServiceProvider;
    private final ApiUpgradeConfigParser configParser;

    public ApiUpgradeInitializer(
        StartParameter startParameter,
        ApiUpgradeServiceProvider apiUpgradeServiceProvider
    ) {
        this.startParameter = startParameter;
        this.apiUpgradeServiceProvider = apiUpgradeServiceProvider;
        this.configParser = new ApiUpgradeConfigParser();
    }

    public void maybeEnableReport() {
        ApiUpgradeConfig config = getApiUpgradeConfig();
        if (config.hasAnyApiUpgrade()) {
            DefaultApiUpgradeProblemCollector collector = new DefaultApiUpgradeProblemCollector(config);
            apiUpgradeServiceProvider.set(collector, config);
            CallsiteApiUpgradeDecoratorRegistry.setCallsiteApiUpgradeDecorator(new DefaultCallsiteApiUpgradeDecorator(collector, config));
            BeanDynamicObjectCallListenerRegistry.setBeanDynamicObjectCallListener(new DefaultSetPropertyApiUpgradeCallListener(collector, config));
        } else {
            disableReport();
        }
    }

    private ApiUpgradeConfig getApiUpgradeConfig() {
        Optional<File> binaryReportFile = getBinaryReportFile();
        if (binaryReportFile.isPresent()) {
            return configParser.parseAcceptedApiChanges(binaryReportFile.get());
        } else {
            return new NoopApiUpgradeConfig();
        }
    }

    private Optional<File> getBinaryReportFile() {
        String path = startParameter != null
            ? startParameter.getSystemPropertiesArgs().get(BINARY_UPGRADE_JSON_PATH)
            : null;
        if (path == null) {
            return Optional.empty();
        } else if (!Files.exists(Paths.get(path))) {
            throw new GradleException("Provided binary upgrade json doesn't exist for path: " + path);
        }
        return Optional.of(new File(path));
    }

    public void disableReport() {
        apiUpgradeServiceProvider.set(new NoopApiUpgradeProblemCollector(), new NoopApiUpgradeConfig());
        CallsiteApiUpgradeDecoratorRegistry.setCallsiteApiUpgradeDecorator(new NoopCallsiteApiUpgradeDecorator());
        BeanDynamicObjectCallListenerRegistry.setBeanDynamicObjectCallListener(new NoopSetPropertyApiUpgradeCallListener());
    }
}
