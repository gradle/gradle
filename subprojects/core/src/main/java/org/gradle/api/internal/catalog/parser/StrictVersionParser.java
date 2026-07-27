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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

import static org.gradle.api.internal.catalog.problems.DefaultCatalogProblemBuilder.VERSION_CATALOG_PROBLEMS;
import static org.gradle.api.internal.catalog.problems.VersionCatalogProblemId.INVALID_VERSION_NOTATION;
import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.util.internal.TextUtil.screamingSnakeToKebabCase;

public class StrictVersionParser {
    private final Interner<String> stringInterner;
    private final Problems problems;

    public StrictVersionParser(Interner<String> stringInterner, Problems problems) {
        this.stringInterner = stringInterner;
        this.problems = problems;
    }

    public RichVersion parse(@Nullable String version) {
        if (version == null) {
            return RichVersion.EMPTY;
        }
        int idx = version.indexOf("!!");
        if (idx == 0) {
            ProblemId problemId = ProblemId.create(
                screamingSnakeToKebabCase(INVALID_VERSION_NOTATION.name()),
                INVALID_VERSION_NOTATION.getDisplayName(),
                GradleCoreProblemGroup.versionCatalog());
            throw problems.getReporter().throwing(new InvalidUserDataException(), problemId, spec -> spec
                .contextualLabel("The strict version modifier (!!) must be appended to a valid version number")
                .details("The strict version modifier syntax expects a base version before '!!'")
                .solution("Place a valid version number before '!!', e.g. '1.0!!'")
                .documentedAt(userManual(VERSION_CATALOG_PROBLEMS, INVALID_VERSION_NOTATION.name().toLowerCase(Locale.ROOT)).getUrl()));
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
