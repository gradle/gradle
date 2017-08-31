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
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.util.GUtil.uncheckedCall;

public abstract class AbstractPluralOutputPropertyAnnotationHandler extends AbstractOutputPropertyAnnotationHandler {

    @Override
    protected void validate(String propertyName, Object value, Collection<String> messages) {
        for (File file : toFiles(value)) {
            doValidate(propertyName, file, messages);
        }
    }

    protected abstract void doValidate(String propertyName, File file, Collection<String> messages);

    @Override
    protected void beforeTask(final Callable<Object> futureValue) {
        for (File file : toFiles(uncheckedCall(futureValue))) {
            doEnsureExists(file);
        }
    }

    protected abstract void doEnsureExists(File file);

    private static Iterable<File> toFiles(Object value) {
        if (value == null) {
            return Collections.emptySet();
        } else if (value instanceof Map) {
            return uncheckedCast(((Map) value).values());
        } else {
            return uncheckedCast(value);
        }
    }
}
