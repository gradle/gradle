/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.nativebinaries.internal.BinaryToolSpec;
import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;

import java.util.ArrayList;
import java.util.List;

class PostTransformActionArgsTransformer<T extends BinaryToolSpec> implements ArgsTransformer<T> {
    private final ArgsTransformer<T> delegate;
    private final Action<List<String>> userTransformer;

    PostTransformActionArgsTransformer(ArgsTransformer<T> delegate, Action<List<String>> userTransformer) {
        this.delegate = delegate;
        this.userTransformer = userTransformer;
    }

    public List<String> transform(T spec) {
        List<String> args = new ArrayList<String>(delegate.transform(spec));
        userTransformer.execute(args);
        return args;
    }
}
