/*
 * Copyright 2023 the original author or authors.
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

package com.tngtech.archunit.library.freeze;

/**
 * Remove this class once ArchUnit 1.0.2 is released and use TextFileBasedViolationStore directly once it becomes public.
 * See: https://github.com/TNG/ArchUnit/pull/1046.
 */
@SuppressWarnings("JavadocLinkAsPlainText")
public class GradleViolationStoreFactory {
    public static ViolationStore create() {
        return new ViolationStoreFactory.TextFileBasedViolationStore();
    }
}
