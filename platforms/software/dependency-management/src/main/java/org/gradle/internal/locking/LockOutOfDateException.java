/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.locking;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphValidationException;
import org.gradle.internal.logging.text.TreeFormatter;

import java.util.List;

import static java.util.Collections.emptyList;

public class LockOutOfDateException extends GraphValidationException {

    private final List<String> errors;

    public static LockOutOfDateException createLockOutOfDateException(String configurationName, Iterable<String> errors) {
        TreeFormatter treeFormatter = new TreeFormatter();
        treeFormatter.node("Dependency lock state for configuration '" + configurationName + "' is out of date");
        treeFormatter.startChildren();
        for (String error : errors) {
            treeFormatter.node(error);
        }
        treeFormatter.endChildren();
        return new LockOutOfDateException(treeFormatter.toString(), ImmutableList.copyOf(errors));
    }

    public LockOutOfDateException(String message) {
        super(message);
        this.errors = emptyList();
    }

    private LockOutOfDateException(String message, List<String> errors) {
        super(message);
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
