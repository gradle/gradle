/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.TransformBackedProvider;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

/**
 * TBD
 */
public class JavaToolchainAwareProvider extends DefaultProvider<JavaToolchain> {

    public JavaToolchainAwareProvider(Callable<? extends JavaToolchain> value) {
        super(value);
    }

    public <S> ProviderInternal<S> mapTool(
        DefaultJavaToolchainUsageProgressDetails.JavaTool tool,
        Transformer<? extends S, ? super JavaToolchain> transformer
    ) {
        return new TransformBackedProvider<S, JavaToolchain>(transformer, this) {
            @Nonnull
            @Override
            protected Value<? extends S> mapValue(Value<? extends JavaToolchain> value) {
                if (value.isMissing()) {
                    return value.asType();
                }
                JavaToolchain toolchain = value.get();
                toolchain.emitUsageEvent(tool);

                return super.mapValue(value);
            }
        };
    }
}
