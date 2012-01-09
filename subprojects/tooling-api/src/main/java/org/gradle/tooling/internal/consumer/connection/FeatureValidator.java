package org.gradle.tooling.internal.consumer.connection;

import org.gradle.tooling.internal.protocol.BuildOperationParametersVersion1;
import org.gradle.tooling.internal.reflect.CompatibleIntrospector;
import org.gradle.tooling.model.internal.Exceptions;
import org.gradle.util.GradleVersion;

/**
 * by Szczepan Faber, created at: 1/9/12
 */
public class FeatureValidator {
    public void validate(String version, BuildOperationParametersVersion1 operationParameters) {
        GradleVersion ver = GradleVersion.version(version);
        if (new CompatibleIntrospector(operationParameters).getSafely(null, "getJavaHome") != null) {
            if (ver.compareTo(GradleVersion.version("1.0-milestone-8")) < 0) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setJavaHome() and buildLauncher.setJavaHome()");
            }
        }
    }
}
