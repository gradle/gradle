package org.gradle.integtests.tooling.r21;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

public class HangingBuildAction implements BuildAction<Void> {
    public Void execute(BuildController controller) {
        throw new RuntimeException("should not be executed");
    }

}
