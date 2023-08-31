/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.file.Directory;
import org.gradle.api.file.SettingsLayout;
import org.gradle.api.internal.SettingsInternal;


public class DefaultSettingsLayout extends AbstractFileSystemLayout implements SettingsLayout {
    private final SettingsInternal settings;

    public DefaultSettingsLayout(SettingsInternal settings, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, FileFactory fileFactory) {
        super(fileResolver, fileCollectionFactory, fileFactory);
        this.settings = settings;
    }

    @Override
    public Directory getSettingsDirectory() {
        return fileFactory.dir(settings.getSettingsDir());
    }

    @Override
    public Directory getRootDirectory() {
        return fileFactory.dir(settings.getRootDir());
    }
}
