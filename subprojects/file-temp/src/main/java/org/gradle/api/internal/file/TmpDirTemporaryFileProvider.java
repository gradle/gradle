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

package org.gradle.api.internal.file;

import org.gradle.internal.Factory;
import org.gradle.internal.FileUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.io.File;

@ServiceScope(Scopes.UserHome.class)
public class TmpDirTemporaryFileProvider extends DefaultTemporaryFileProvider {

    @Inject
    TmpDirTemporaryFileProvider() {
        super(new Factory<File>() {
            @Override
            public File create() {
                @SuppressWarnings("deprecation") final String tempDirLocation = SystemProperties.getInstance().getJavaIoTmpDir();
                return FileUtils.canonicalize(new File(tempDirLocation));
            }
        });
    }

    private TmpDirTemporaryFileProvider(final Factory<File> tempDirProvider) {
        super(new Factory<File>() {
            @Override
            public File create() {
                return FileUtils.canonicalize(tempDirProvider.create());
            }
        });
    }

    public static TmpDirTemporaryFileProvider createLegacy() {
        return new TmpDirTemporaryFileProvider();
    }

    public static TmpDirTemporaryFileProvider createFromCustomBase(Factory<File> tempDirProvider) {
        return new TmpDirTemporaryFileProvider(tempDirProvider);
    }
}
