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

package org.gradle.internal.io;

import javax.annotation.Nullable;

public class SkipFirstTextStream implements TextStream {

    private boolean open;
    private final TextStream delegate;

    public SkipFirstTextStream(TextStream delegate) {
        this.delegate = delegate;
    }

    @Override
    public void text(String text) {
        if (open) {
            delegate.text(text);
        } else {
            open = true;
        }
    }

    @Override
    public void endOfStream(@Nullable Throwable failure) {
        delegate.endOfStream(failure);
    }

}
