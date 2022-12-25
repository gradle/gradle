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

package org.gradle.api.internal.file

import org.gradle.api.file.RegularFile
import org.gradle.api.internal.provider.PropertyInternal
import org.gradle.internal.state.ManagedFactory

class FilePropertyTest extends FileSystemPropertySpec<RegularFile> {
    @Override
    Class<RegularFile> type() {
        return RegularFile.class
    }

    @Override
    RegularFile someValue() {
        return baseDirectory.file("dir1").get()
    }

    @Override
    RegularFile someOtherValue() {
        return baseDirectory.file("other1").get()
    }

    @Override
    RegularFile someOtherValue2() {
        return baseDirectory.file("other2").get()
    }

    @Override
    RegularFile someOtherValue3() {
        return baseDirectory.file("other3").get()
    }

    @Override
    PropertyInternal<RegularFile> propertyWithNoValue() {
        return factory.newFileProperty()
    }

    @Override
    ManagedFactory managedFactory() {
        new ManagedFactories.RegularFilePropertyManagedFactory(factory)
    }
}
