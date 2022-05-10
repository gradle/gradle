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

package org.gradle.api.plugins.jvm;

import groovy.lang.MissingMethodException;
import org.gradle.api.Incubating;

/**
 * Represents the case when a method called on {@link JvmComponentDependencies} is absent, but is present on the
 * top-level {@link org.gradle.api.artifacts.dsl.DependencyHandler}.
 *
 * @since 7.6
 */
@Incubating
public final class MissingDependenciesMethodException extends RuntimeException {
    private final Class<?> currentBlock;
    private final Class<?> intendedBlock;
    private final String defaultMissingMethodMessage;
    public MissingDependenciesMethodException(MissingMethodException e, Class<?> currentBlock, Class<?> intendedBlock) {
        super(e);
        this.defaultMissingMethodMessage = (e.getCause() != null) ? e.getCause().getMessage() : e.getMessage();
        this.currentBlock = currentBlock;
        this.intendedBlock = intendedBlock;
    }

    @Override
    public String getMessage() {
        return defaultMissingMethodMessage
                + "\nThis method is present in the top-level dependencies block, but can not be used within a test suite's dependencies block."
                + "\nSee https://docs.gradle.org/current/userguide/jvm_test_suite_plugin.html for an overview on the differences between these two blocks, or compare the following DSL references:"
                + "\n\t" + dslLinkFor(currentBlock)
                + "\n\t" + dslLinkFor(intendedBlock);
    }

    private String dslLinkFor(Class<?> block) {
        String cleansedName = block.getName().endsWith("_Decorated") ? block.getName().substring(0, block.getName().length() - "_Decorated".length()) : block.getName();
        return "https://docs.gradle.org/current/dsl/" + cleansedName + ".html";
    }
}
