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

package org.gradle.api.internal.file;

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFilePermissions;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.SyncSpec;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.Actions;


public class DefaultFileSystemOperations implements FileSystemOperations {

    private final ObjectFactory objectFactory;

    private final FileOperations fileOperations;

    public DefaultFileSystemOperations(ObjectFactory objectFactory, FileOperations fileOperations) {
        this.objectFactory = objectFactory;
        this.fileOperations = fileOperations;
    }

    @Override
    public CopySpec copySpec(Action<? super CopySpec> action) {
        return Actions.with(copySpec(), action);
    }

    @Override
    public CopySpec copySpec() {
        return fileOperations.copySpec();
    }

    @Override
    public WorkResult copy(Action<? super CopySpec> action) {
        return fileOperations.copy(action);
    }

    @Override
    public WorkResult sync(Action<? super SyncSpec> action) {
        return fileOperations.sync(action);
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        return fileOperations.delete(action);
    }

    @Override
    public ConfigurableFilePermissions filePermissions(Action<? super ConfigurableFilePermissions> configureAction) {
        return permissions(false, configureAction);
    }

    @Override
    public ConfigurableFilePermissions directoryPermissions(Action<? super ConfigurableFilePermissions> configureAction) {
        return permissions(true, configureAction);
    }

    @Override
    public ConfigurableFilePermissions permissions(String unixNumericOrSymbolic) {
        return permissions(false, filePermissions -> filePermissions.unix(unixNumericOrSymbolic));
    }

    @Override
    public ConfigurableFilePermissions permissions(int unixNumeric) {
        return permissions(false, filePermissions -> filePermissions.unix(unixNumeric));
    }

    @Override
    public Provider<ConfigurableFilePermissions> permissions(Provider<String> permissions) {
        return permissions.map(SerializableLambdas.transformer(this::permissions));
    }

    private ConfigurableFilePermissions permissions(boolean directory, Action<? super ConfigurableFilePermissions> configureAction) {
        ConfigurableFilePermissions permissions = objectFactory.newInstance(DefaultConfigurableFilePermissions.class, objectFactory, DefaultConfigurableFilePermissions.getDefaultUnixNumeric(directory));
        configureAction.execute(permissions);
        return permissions;
    }
}
