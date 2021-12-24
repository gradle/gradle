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

package org.gradle.api.internal.provider.sources.process;

import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.sources.process.ProcessOutputValueSource.ExecOutputData;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecOutput;
import org.gradle.process.ExecResult;

import java.nio.charset.Charset;

public class DefaultExecOutput implements ExecOutput {
    private final Provider<ExecOutputData> dataProvider;

    public DefaultExecOutput(Provider<ExecOutputData> dataProvider) {
        this.dataProvider = dataProvider;
    }

    @Override
    public Provider<ExecResult> getResult() {
        return dataProvider.map(SerializableLambdas.transformer(ExecOutputData::getResult));
    }

    @Override
    public StandardStreamContent getStandardOutput() {
        return new DefaultStandardStreamContent(dataProvider.map(SerializableLambdas.transformer(ExecOutputData::getOutput)));
    }

    @Override
    public StandardStreamContent getStandardError() {
        return new DefaultStandardStreamContent(dataProvider.map(SerializableLambdas.transformer(ExecOutputData::getError)));
    }

    private static class DefaultStandardStreamContent implements StandardStreamContent {
        private final Provider<byte[]> bytesProvider;

        public DefaultStandardStreamContent(Provider<byte[]> bytesProvider) {
            this.bytesProvider = bytesProvider;
        }

        @Override
        public Provider<String> getAsText() {
            return getAsBytes().map(SerializableLambdas.transformer(bytes -> new String(bytes, Charset.defaultCharset())));
        }

        @Override
        public Provider<byte[]> getAsBytes() {
            return bytesProvider;
        }
    }
}
