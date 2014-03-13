/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.nativebinaries.toolchain.internal.ArgsTransformer;
import org.gradle.nativebinaries.toolchain.internal.NativeCompileSpec;

import java.util.List;

public class ShortCircuitArgsTransformer<T extends NativeCompileSpec> implements ArgsTransformer<T>  {
    private ArgsTransformer<T> delegate;
    private List<String> cachedArguments;
    private T cachedSpec;

    public ShortCircuitArgsTransformer(ArgsTransformer<T> delegate) {
        this.delegate = delegate;
    }

    public List<String> transform(T spec) {
        if(spec.equals(cachedSpec)){
            return cachedArguments;
        }
        cachedArguments = delegate.transform(spec);
        this.cachedSpec = spec;
        return cachedArguments;
    }
}
