/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.performance.android;

import com.android.builder.model.AndroidProject;
import org.gradle.api.Action;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.ProjectConnection;

import java.util.Map;

public class SyncAction {
    // DO NOT change the signature of this method: it is a convention used in
    // our internal performance testing infrastructure
    public static void withProjectConnection(ProjectConnection connect, Action<? super BuildActionExecuter<Map<String, AndroidProject>>> modelBuilderAction) {

        System.out.println("* Running sync");

        Timer syncTimer = new Timer();

        BuildActionExecuter<Map<String, AndroidProject>> modelBuilder = connect.action(new GetModel());
        modelBuilder.setStandardOutput(System.out);
        modelBuilder.setStandardError(System.err);
        modelBuilder.withArguments("-Dcom.android.build.gradle.overrideVersionCheck=true",
            "-Pandroid.injected.build.model.only=true",
            "-Pandroid.injected.invoked.from.ide=true",
            "-Pandroid.injected.build.model.only.versioned=2");
        modelBuilder.setJvmArguments("-Xmx2g");
        if (modelBuilderAction != null) {
            modelBuilderAction.execute(modelBuilder);
        }

        Timer actionTimer = new Timer();
        Map<String, AndroidProject> models = modelBuilder.run();
        actionTimer.stop();
        System.out.println("Running action took " + actionTimer.duration());

        System.out.println("Received models: " + models.size());

        new Inspector().inspectModel(models);
        syncTimer.stop();
        System.out.println("Sync took " + syncTimer.duration());
    }
}
