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
        //TODO SF don't need an introspector here, as we're in the consumer. Refactor.
        CompatibleIntrospector introspector = new CompatibleIntrospector(operationParameters);
        if (introspector.isConfigured("getJavaHome")) {
            if (lessThan(ver, "1.0-milestone-8")) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setJavaHome() and buildLauncher.setJavaHome()");
            }
        }
        if (introspector.isConfigured("getJvmArguments")) {
            if (lessThan(ver, "1.0-milestone-8")) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setJvmArguments() and buildLauncher.setJvmArguments()");
            }
        }
        if (introspector.isConfigured("getStandardInput")) {
            if (lessThan(ver, "1.0-milestone-8")) {
                throw Exceptions.unsupportedOperationConfiguration("modelBuilder.setStandardInput() and buildLauncher.setStandardInput()");
            }
        }
    }

    private boolean lessThan(GradleVersion ver, String version) {
        if (ver.isSnapshot() && ver.getVersion().startsWith(version)) {
            return false;
        }
        return ver.compareTo(GradleVersion.version(version)) < 0;
    }
}
