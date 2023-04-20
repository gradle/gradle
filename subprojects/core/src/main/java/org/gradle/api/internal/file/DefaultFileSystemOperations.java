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
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.FileAccessPermissions;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.WorkResult;


public class DefaultFileSystemOperations implements FileSystemOperations {

    private final ObjectFactory objectFactory;

    private final FileOperations fileOperations;

    public DefaultFileSystemOperations(ObjectFactory objectFactory, FileOperations fileOperations) {
        this.objectFactory = objectFactory;
        this.fileOperations = fileOperations;
    }

    @Override
    public WorkResult copy(Action<? super CopySpec> action) {
        return fileOperations.copy(action);
    }

    @Override
    public WorkResult sync(Action<? super CopySpec> action) {
        return fileOperations.sync(action);
    }

    @Override
    public WorkResult delete(Action<? super DeleteSpec> action) {
        return fileOperations.delete(action);
    }

    @Override
    public FileAccessPermissions filePermissions(Action<? super FileAccessPermissions> configureAction) {
        return permissions(false, configureAction);
    }

    @Override
    public FileAccessPermissions directoryPermissions(Action<? super FileAccessPermissions> configureAction) {
        return permissions(true, configureAction);
    }

    @Override
    public FileAccessPermissions permissions(String permissions) {
        return permissions(false, fileAccessPermissions -> fileAccessPermissions.unix(permissions));
    }

    @Override
    public FileAccessPermissions permissions(Provider<String> permissions) {
        return permissions(false, fileAccessPermissions -> fileAccessPermissions.unix(permissions));
    }

    private FileAccessPermissions permissions(boolean directory, Action<? super FileAccessPermissions> configureAction) {
        FileAccessPermissions permissions = objectFactory.newInstance(DefaultFileAccessPermissions.class, objectFactory, DefaultFileAccessPermissions.getDefaultUnixNumeric(directory));
        configureAction.execute(permissions);
        return permissions;
    }
}
