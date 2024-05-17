/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.internal.ExecException;
import org.gradle.process.internal.ExecHandleBuilder;
import org.gradle.process.internal.ExecHandleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class OsXInstallationSupplier extends AutoDetectingInstallationSupplier {

    private static final Logger LOGGER = LoggerFactory.getLogger(OsXInstallationSupplier.class);

    private final ExecHandleFactory execHandleFactory;
    private final OperatingSystem os;

    public OsXInstallationSupplier(ExecHandleFactory execHandleFactory, ProviderFactory providerFactory, OperatingSystem os) {
        super(providerFactory);
        this.execHandleFactory = execHandleFactory;
        this.os = os;
    }

    @Override
    public String getSourceName() {
        return "MacOS java_home";
    }

    @Override
    protected Set<InstallationLocation> findCandidates() {
        if (os.isMacOsX()) {
            try {
                final Reader output = executeJavaHome();
                final Set<File> javaHomes = new OsXJavaHomeOutputParser().parse(output);
                return javaHomes.stream().map(this::asInstallation).collect(Collectors.toSet());
            } catch (ExecException e) {
                String errorMessage = "Java Toolchain auto-detection failed to find local MacOS system JVMs";
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(errorMessage, e);
                } else {
                    LOGGER.info(errorMessage);
                }
            }
        }
        return Collections.emptySet();
    }

    private InstallationLocation asInstallation(File javaHome) {
        return new InstallationLocation(javaHome, getSourceName());
    }

    private Reader executeJavaHome() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        executeCommand(outputStream);
        return new InputStreamReader(new ByteArrayInputStream(outputStream.toByteArray()));
    }

    void executeCommand(ByteArrayOutputStream outputStream) {
        ExecHandleBuilder execHandleBuilder = execHandleFactory.newExec();
        execHandleBuilder.workingDir(new File(".").getAbsoluteFile());
        execHandleBuilder.commandLine("/usr/libexec/java_home", "-V");
        execHandleBuilder.getEnvironment().remove("JAVA_VERSION"); //JAVA_VERSION filters the content of java_home's output
        execHandleBuilder.setErrorOutput(outputStream); // verbose output is written to stderr
        execHandleBuilder.setStandardOutput(new ByteArrayOutputStream());
        execHandleBuilder.build().start().waitForFinish().assertNormalExitValue();
    }

}
