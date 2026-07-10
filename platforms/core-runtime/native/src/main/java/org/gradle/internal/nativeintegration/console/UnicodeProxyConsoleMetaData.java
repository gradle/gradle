/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.nativeintegration.console;

import org.jspecify.annotations.NullMarked;

@NullMarked
public abstract class UnicodeProxyConsoleMetaData implements ConsoleMetaData {

    protected final ConsoleMetaData metaData;

    public UnicodeProxyConsoleMetaData(ConsoleMetaData metaData) {
        this.metaData = metaData;
    }

    @Override
    public boolean isStdOutATerminal() {
        return metaData.isStdOutATerminal();
    }

    @Override
    public boolean isStdErrATerminal() {
        return metaData.isStdErrATerminal();
    }

    @Override
    public int getCols() {
        return metaData.getCols();
    }

    @Override
    public int getRows() {
        return metaData.getRows();
    }

    @Override
    public boolean isWrapStreams() {
        return metaData.isWrapStreams();
    }

    public static final class FixedUnicodeSupport extends UnicodeProxyConsoleMetaData {
        private final boolean supportsUnicode;

        public FixedUnicodeSupport(ConsoleMetaData metaData, boolean supportsUnicode) {
            super(metaData);
            this.supportsUnicode = supportsUnicode;
        }

        @Override
        public boolean supportsUnicode() {
            return supportsUnicode;
        }
    }

    @Override
    public boolean supportsTaskbarProgress() {
        return metaData.supportsTaskbarProgress();
    }
}
