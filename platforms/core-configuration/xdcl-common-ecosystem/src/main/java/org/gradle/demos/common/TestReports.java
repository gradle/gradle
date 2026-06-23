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

package org.gradle.demos.common;

import org.gradle.api.file.DirectoryProperty;

/**
 * The test report locations of a JVM build model, populated by the reaction from the registered
 * {@code test} task. Shared by the {@code JavaLibraryModel} and {@code GroovyLibraryModel}.
 */
public interface TestReports {

    DirectoryProperty getHtmlReportDir();

    DirectoryProperty getJunitXmlReportDir();
}
