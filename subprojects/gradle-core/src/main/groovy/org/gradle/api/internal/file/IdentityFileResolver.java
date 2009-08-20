/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.PathValidation;
import java.io.File;

/**
 * FileResolver that uses the file provided to it or constructs one from the toString of the provided object.
 * Used in cases where a FileResolver is needed by the infrastructure, but no base directory can be known.
 * @author Steve Appling
 */
public class IdentityFileResolver extends AbstractFileResolver{
    public File resolve(Object path) {
        return resolve(path, PathValidation.NONE);
    }

    public File resolve(Object path, PathValidation validation) {
        File file = convertObjectToFile(path);

        validate(file, validation);
        return file;
    }
}
