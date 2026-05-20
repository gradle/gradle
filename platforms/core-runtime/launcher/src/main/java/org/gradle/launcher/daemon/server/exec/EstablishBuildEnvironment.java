/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.internal.FileUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;

import java.io.File;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Applies the system properties and process directory specified by the client to the daemon JVM,
 * and restores the previous values when the build finishes.
 *
 * <p>This action is intentionally placed before {@link LogToClient} in the daemon command chain so
 * that system properties such as {@link LogToClient#DISABLE_OUTPUT} are visible to {@code LogToClient}
 * when it decides whether to forward output to the client.</p>
 *
 * <p>Applying client-supplied environment variables is the responsibility of {@link ApplyClientEnvironmentVariables},
 * which runs <em>after</em> {@code LogToClient} so that any warning emitted on environment-modification
 * failure (e.g. when the native integration is unavailable on a {@code noexec} filesystem) reaches the client.</p>
 */
public class EstablishBuildEnvironment extends BuildCommandOnly {

    private final ProcessEnvironment processEnvironment;

    public EstablishBuildEnvironment(ProcessEnvironment processEnvironment) {
        this.processEnvironment = processEnvironment;
    }

    @Override
    protected void doBuild(DaemonCommandExecution execution, Build build) {
        Properties originalSystemProperties = new Properties();
        originalSystemProperties.putAll(System.getProperties());
        File originalProcessDir = FileUtils.canonicalize(new File("."));

        for (Map.Entry<String, String> entry : build.getParameters().getSystemProperties().entrySet()) {
            if (SystemProperties.getInstance().isStandardProperty(entry.getKey())) {
                continue;
            }
            if (SystemProperties.getInstance().isNonStandardImportantProperty(entry.getKey())) {
                continue;
            }
            if (entry.getKey().startsWith("sun.") || entry.getKey().startsWith("awt.") || entry.getKey().contains(".awt.")) {
                continue;
            }
            System.setProperty(entry.getKey(), entry.getValue());
        }

        processEnvironment.maybeSetProcessDir(build.getParameters().getCurrentDir());

        // Capture and restore this in case the build code calls Locale.setDefault()
        Locale locale = Locale.getDefault();

        try {
            execution.proceed();
        } finally {
            System.setProperties(originalSystemProperties);
            processEnvironment.maybeSetProcessDir(originalProcessDir);
            Locale.setDefault(locale);
        }
    }
}
