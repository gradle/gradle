/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaDevelopmentKit;
import org.gradle.jvm.toolchain.JavaInstallation;
import org.gradle.jvm.toolchain.JavaInstallationRegistry;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Optional;
import java.util.concurrent.Callable;

@ServiceScope(Scopes.Build.class)
public class DefaultJavaInstallationRegistry implements JavaInstallationRegistry {
    private final JavaInstallationProbe installationProbe;
    private final ProviderFactory providerFactory;
    private final FileCollectionFactory fileCollectionFactory;
    private final FileFactory fileFactory;

    public DefaultJavaInstallationRegistry(JavaInstallationProbe installationProbe, ProviderFactory providerFactory, FileCollectionFactory fileCollectionFactory, FileFactory fileFactory) {
        this.installationProbe = installationProbe;
        this.providerFactory = providerFactory;
        this.fileCollectionFactory = fileCollectionFactory;
        this.fileFactory = fileFactory;
    }

    @Override
    public Provider<JavaInstallation> getInstallationForCurrentVirtualMachine() {
        return Providers.of(new DefaultJavaInstallation(installationProbe.current(), fileCollectionFactory, fileFactory));
    }

    @Override
    public Provider<JavaInstallation> installationForDirectory(Directory javaHomeDir) {
        // TODO - should be a value source and so a build input if queried during configuration time
        // TODO - provider should advertise the type of value it produces
        // TODO - display name
        return providerFactory.provider(new Callable<JavaInstallation>() {
            private DefaultJavaInstallation value;

            @Override
            public JavaInstallation call() {
                if (value == null) {
                    try {
                        if (!javaHomeDir.getAsFile().exists()) {
                            throw new FileNotFoundException(String.format("Directory %s does not exist.", javaHomeDir.getAsFile()));
                        }
                        value = new DefaultJavaInstallation(installationProbe.checkJdk(javaHomeDir.getAsFile()), fileCollectionFactory, fileFactory);
                    } catch (Exception e) {
                        throw new JavaInstallationDiscoveryException(String.format("Could not determine the details of Java installation in directory %s.", javaHomeDir), e);
                    }
                }
                return value;
            }
        });
    }

    @Override
    public Provider<JavaInstallation> installationForDirectory(Provider<Directory> installationDirectory) {
        return installationDirectory.flatMap(this::installationForDirectory);
    }

    @Contextual
    public static class JavaInstallationDiscoveryException extends GradleException {
        public JavaInstallationDiscoveryException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }

    private static class DefaultJavaInstallation implements JavaInstallation {
        private final Jvm jvm;
        private final String implementationName;
        private final FileCollectionFactory fileCollectionFactory;
        private final FileFactory fileFactory;

        public DefaultJavaInstallation(JavaInstallationProbe.ProbeResult probeResult, FileCollectionFactory fileCollectionFactory, FileFactory fileFactory) {
            this.jvm = Jvm.discovered(probeResult.getJavaHome().toFile(), probeResult.getImplementationJavaVersion(), probeResult.getJavaVersion());
            this.implementationName = probeResult.getImplementationName();
            this.fileCollectionFactory = fileCollectionFactory;
            this.fileFactory = fileFactory;
        }

        @Override
        public JavaVersion getJavaVersion() {
            return jvm.getJavaVersion();
        }

        @Override
        public Directory getInstallationDirectory() {
            return fileFactory.dir(jvm.getJavaHome());
        }

        @Override
        public RegularFile getJavaExecutable() {
            return fileFactory.file(jvm.getJavaExecutable());
        }

        @Override
        public String getImplementationName() {
            return implementationName;
        }

        @Override
        public Optional<JavaDevelopmentKit> getJdk() {
            if (jvm.isJdk()) {
                return Optional.of(new JavaDevelopmentKit() {
                    @Override
                    public RegularFile getJavacExecutable() {
                        return fileFactory.file(jvm.getJavacExecutable());
                    }

                    @Override
                    public RegularFile getJavadocExecutable() {
                        return fileFactory.file(jvm.getJavadocExecutable());
                    }

                    @Override
                    public FileCollection getToolsClasspath() {
                        File toolsJar = jvm.getToolsJar();
                        if (toolsJar != null) {
                            return fileCollectionFactory.fixed(toolsJar);
                        } else {
                            return fileCollectionFactory.empty();
                        }
                    }
                });
            } else {
                return Optional.empty();
            }
        }
    }
}
