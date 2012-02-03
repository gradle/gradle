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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Aims to make the local environment the same as the client's environment.
 */
public class EstablishBuildEnvironment extends BuildCommandOnly {
    private final ProcessEnvironment processEnvironment;
    private final static Logger LOGGER = Logging.getLogger(EstablishBuildEnvironment.class);

    public EstablishBuildEnvironment(ProcessEnvironment processEnvironment) {
        this.processEnvironment = processEnvironment;
    }

    protected void doBuild(DaemonCommandExecution execution, Build build) {
        Properties originalSystemProperties = new Properties();
        originalSystemProperties.putAll(System.getProperties());
        File currentDir = GFileUtils.canonicalise(new File("."));

        Properties clientSystemProperties = new Properties();
        clientSystemProperties.putAll(build.getParameters().getSystemProperties());

        //Let's ignore client's java.home
        //We want to honour the java.home configured when the daemon process was started
        //It does not make sense to update this property per job anyway as we have a daemon per java home
        clientSystemProperties.put("java.home", originalSystemProperties.get("java.home"));

        System.setProperties(clientSystemProperties);

        Map<String, String> originalEnv = System.getenv();
        LOGGER.debug("Configuring env variables: {}", build.getParameters().getEnvVariables());
        processEnvironment.maybeSetEnvironment(build.getParameters().getEnvVariables());

        processEnvironment.maybeSetProcessDir(build.getParameters().getCurrentDir());

        try {
            execution.proceed();
        } finally {
            System.setProperties(originalSystemProperties);
            processEnvironment.maybeSetEnvironment(originalEnv);
            processEnvironment.maybeSetProcessDir(currentDir);
        }
    }

}
