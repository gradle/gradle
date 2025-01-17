/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.api.NonNullApi;
import org.gradle.process.BaseExecSpec;

import javax.annotation.Nullable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@NonNullApi
public interface BaseExecHandleBuilder {

    BaseExecHandleBuilder setDisplayName(@Nullable String displayName);
    BaseExecHandleBuilder setStandardOutput(OutputStream outputStream);
    BaseExecHandleBuilder setStandardInput(InputStream inputStream);
    BaseExecHandleBuilder setErrorOutput(OutputStream outputStream);
    BaseExecHandleBuilder setExecutable(String s);
    BaseExecHandleBuilder setWorkingDir(File asFile);
    BaseExecHandleBuilder setEnvironment(Map<String, Object> stringObjectMap);

    default BaseExecHandleBuilder configureFrom(BaseExecSpec spec) {
        if (spec.getStandardInput().isPresent()) {
            setStandardInput(spec.getStandardInput().get());
        }
        if (spec.getStandardOutput().isPresent()) {
            setStandardOutput(spec.getStandardOutput().get());
        }
        if (spec.getErrorOutput().isPresent()) {
            setErrorOutput(spec.getErrorOutput().get());
        }
        if (spec.getExecutable().isPresent()) {
            setExecutable(spec.getExecutable().get());
        }
        if (spec.getWorkingDir().isPresent()) {
            setWorkingDir(spec.getWorkingDir().get().getAsFile());
        }
        setEnvironment(spec.getEnvironment().get());
        return this;
    }

    /**
     * Adds a listener to the list of ExecHandle listeners..
     */
    BaseExecHandleBuilder listener(ExecHandleListener listener);

    ExecHandle build();

}
