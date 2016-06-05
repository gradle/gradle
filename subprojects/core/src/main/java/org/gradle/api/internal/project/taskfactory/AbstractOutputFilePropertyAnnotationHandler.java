/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.project.taskfactory;

import java.io.File;
import java.util.Collection;

public abstract class AbstractOutputFilePropertyAnnotationHandler extends AbstractOutputPropertyAnnotationHandler {
    protected static void validateFile(String propertyName, File file, Collection<String> messages) {
        if (file.exists() && file.isDirectory()) {
            messages.add(String.format("Cannot write to file '%s' specified for property '%s' as it is a directory.", file, propertyName));
        }

        for (File candidate = file.getParentFile(); candidate != null && !candidate.isDirectory(); candidate = candidate.getParentFile()) {
            if (candidate.exists() && !candidate.isDirectory()) {
                messages.add(String.format("Cannot write to file '%s' specified for property '%s', as ancestor '%s' is not a directory.", file, propertyName, candidate));
                break;
            }
        }
    }
}
