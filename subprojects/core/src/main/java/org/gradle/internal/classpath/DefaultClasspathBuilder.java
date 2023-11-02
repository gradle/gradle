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

package org.gradle.internal.classpath;

import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Default implementation of {@link ClasspathBuilder} that is registered also as a service.
 *
 * This implementation first writes Jar file to a temp file and then moves the result to the destination file.
 * You can use @{@link InPlaceClasspathBuilder} if you want to avoid this indirection and write directly to the destination file.
 */
@NonNullApi
@ServiceScope(Scopes.UserHome.class)
public class DefaultClasspathBuilder implements ClasspathBuilder {

    private final TemporaryFileProvider temporaryFileProvider;
    private final ClasspathBuilder inPlaceBuilder;

    @Inject
    public DefaultClasspathBuilder(final TemporaryFileProvider temporaryFileProvider) {
        this.temporaryFileProvider = temporaryFileProvider;
        this.inPlaceBuilder = new InPlaceClasspathBuilder();
    }

    @Override
    public void jar(File jarFile, Action action) {
        try {
            buildJar(jarFile, action);
        } catch (Exception e) {
            throw new GradleException(String.format("Failed to create Jar file %s.", jarFile), e);
        }
    }

    private void buildJar(File jarFile, Action action) throws IOException {
        File parentDir = jarFile.getParentFile();
        File tmpFile = temporaryFileProvider.createTemporaryFile(jarFile.getName(), ".tmp");
        try {
            Files.createDirectories(parentDir.toPath());
            inPlaceBuilder.jar(tmpFile, action);
            Files.move(tmpFile.toPath(), jarFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmpFile.toPath());
        }
    }
}
