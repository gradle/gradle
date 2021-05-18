/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;

import java.util.ArrayList;
import java.util.List;

public class ProviderAwareJvmOptions extends JvmOptions {
    private List<CommandLineArgumentProvider> jvmArgumentProviders;

    public ProviderAwareJvmOptions(FileCollectionFactory fileCollectionFactory, JavaDebugOptions debugOptions) {
        super(fileCollectionFactory, debugOptions);
    }

    public ProviderAwareJvmOptions(FileCollectionFactory fileCollectionFactory) {
        super(fileCollectionFactory, new DefaultJavaDebugOptions());
    }

    @Override
    public List<String> getAllJvmArgs() {
        if (hasJvmArgumentProviders()) {
            JvmOptions copy = createCopy();
            for (CommandLineArgumentProvider jvmArgumentProvider : jvmArgumentProviders) {
                copy.jvmArgs(jvmArgumentProvider.asArguments());
            }
            return copy.getAllJvmArgs();
        } else {
            return super.getAllJvmArgs();
        }
    }

    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        super.setAllJvmArgs(arguments);
        if (hasJvmArgumentProviders()) {
            jvmArgumentProviders.clear();
        }
    }

    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        if (jvmArgumentProviders == null) {
            jvmArgumentProviders = new ArrayList<>();
        }
        return jvmArgumentProviders;
    }

    boolean hasJvmArgumentProviders() {
        return jvmArgumentProviders != null && !jvmArgumentProviders.isEmpty();
    }

}
