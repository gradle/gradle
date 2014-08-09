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
package org.gradle.nativebinaries.toolchain.internal;

import org.gradle.api.Transformer;
import org.gradle.nativebinaries.internal.BinaryToolSpec;

import java.util.ArrayList;
import java.util.List;

public class CompileSpecToArgsTransformerChain<T extends BinaryToolSpec> implements Transformer<List<String>, T> {
    private final Transformer<List<String>, T> delegate;
    private final List<Transformer<List<String>, List<String>>> transformers = new ArrayList<Transformer<List<String>, List<String>>>();

    public CompileSpecToArgsTransformerChain(Transformer<List<String>, T> delegate) {
        this.delegate = delegate;
    }

    public List<String> transform(T spec) {
        List<String> args = delegate.transform(spec);
        for (Transformer<List<String>, List<String>> transformer : transformers) {
            args = transformer.transform(args);
        }
        return args;
    }

    public CompileSpecToArgsTransformerChain<T> withTransformation(Transformer<List<String>, List<String>> transformer) {
        transformers.add(transformer);
        return this;
    }
}
