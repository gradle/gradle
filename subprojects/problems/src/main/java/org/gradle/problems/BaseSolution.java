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
package org.gradle.problems;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class BaseSolution implements Solution {
    private final Supplier<String> shortDescription;
    private final Supplier<String> longDescription;
    private final Supplier<String> documentationLink;

    public BaseSolution(Supplier<String> shortDescription,
                        Supplier<String> longDescription,
                        Supplier<String> documentationLink) {
        this.shortDescription = Objects.requireNonNull(shortDescription, "short description supplier must not be null");
        this.longDescription = Objects.requireNonNull(longDescription, "long description supplier must not be null");
        this.documentationLink = Objects.requireNonNull(documentationLink, "documentation link supplier must not be null");
    }

    @Override
    public String getShortDescription() {
        return shortDescription.get();
    }

    @Override
    public Optional<String> getLongDescription() {
        return Optional.ofNullable(longDescription.get());
    }

    @Override
    public Optional<String> getDocumentationLink() {
        return Optional.ofNullable(documentationLink.get());
    }
}
