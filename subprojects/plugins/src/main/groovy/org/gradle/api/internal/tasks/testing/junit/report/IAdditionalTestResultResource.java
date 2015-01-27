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
