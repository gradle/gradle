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
package org.gradle.api.internal.catalog.parser;

import com.google.common.collect.Interner;
import org.gradle.api.InvalidUserCodeException;

import javax.annotation.Nullable;

public class StrictVersionParser {
    private final Interner<String> stringInterner;

    public StrictVersionParser(Interner<String> stringInterner) {
        this.stringInterner = stringInterner;
    }

    public RichVersion parse(@Nullable String version) {
        if (version == null) {
            return RichVersion.EMPTY;
        }
        int idx = version.indexOf("!!");
        if (idx == 0) {
            throw new InvalidUserCodeException("The strict version modifier (!!) must be appended to a valid version number");
        }
        if (idx > 0) {
            String strictly = stringInterner.intern(version.substring(0, idx));
            String prefer = stringInterner.intern(version.substring(idx+2));
            return new RichVersion(null, strictly, prefer);
        }
        return new RichVersion(stringInterner.intern(version), null, null);
    }

    public static class RichVersion {
        public static final RichVersion EMPTY = new RichVersion(null, null, null);

        public final String require;
        public final String strictly;
        public final String prefer;

        private RichVersion(@Nullable String require, @Nullable String strictly, @Nullable String prefer) {
            this.require = require;
            this.strictly = strictly;
            this.prefer = prefer;
        }
    }
}
