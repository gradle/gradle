/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.report;

import java.io.File;
import java.util.List;

/**
 * This interface is inteded to be used in case a Testreport implementation allows to have additional Resources to be
 * rendered within the Test report.
 * @see org.gradle.api.internal.tasks.testing.junit.report.DefaultTestReport
 */
public interface IAdditionalTestResultResource {
    List<File> findResources(TestResult test);
}
