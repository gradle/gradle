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

import org.gradle.internal.lazy.Lazy;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ApiUpgradeReporterFactory {

    private static final String BINARY_UPGRADE_JSON_PATH = "org.gradle.binary.upgrade.report.json";

    private final AtomicReference<Lazy<ApiUpgradeReporter>> lazyApiUpgrader = new AtomicReference<>(initNewCollector());

    private Lazy<ApiUpgradeReporter> initNewCollector() {
        return Lazy.locking().of(() -> {
            String path = System.getProperty(BINARY_UPGRADE_JSON_PATH);
            if (path == null || !Files.exists(Paths.get(path))) {
                return ApiUpgradeReporter.noUpgrades();
            }
            File file = new File(path);
            List<ReportableApiChange> changes = new ApiUpgradeJsonParser().parseAcceptedApiChanges(file);
            ApiUpgradeReporter reporter = ApiUpgradeReporter.newApiUpgradeReporter(changes);
            DynamicGroovyApiUpgradeDecorator.init(reporter, changes);
            return reporter;
        });
    }

    public ApiUpgradeReporter getApiUpgrader() {
        return lazyApiUpgrader.get().get();
    }

    public ApiUpgradeReporter getApiUpgraderAndRelease() {
        return lazyApiUpgrader.getAndSet(initNewCollector()).get();
    }
}
