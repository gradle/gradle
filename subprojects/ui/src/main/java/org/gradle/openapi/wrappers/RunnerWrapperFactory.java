/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.openapi.wrappers;

import org.gradle.openapi.external.runner.GradleRunnerInteractionVersion1;
import org.gradle.openapi.external.runner.GradleRunnerVersion1;
import org.gradle.openapi.wrappers.runner.GradleRunnerWrapper;

import java.io.File;

/**
 * This factory instantiates GradleRunnerWrappers by an external process. It is meant to be called via the Open API GradleRunnerFactory class using reflection. This is because it is called
 * dynamically. It is also meant to help shield a Gradle user from changes to different versions of the GradleRunner. It does so by using wrappers that can dynamically choose what/how to implement.
 * The wrappers usually use the latest, however, some of the functionality requires a matching Open API jar (which will be included with the external process trying to use this). If the matching
 * functionality is not found (a NoClassDefFoundError is thrown), it will fall back to earlier versions.
 *
 * This class should not be moved or renamed, nor should its functions be renamed or have arguments added to/removed from them. This is to ensure forward/backward compatibility with multiple versions
 * of IDE plugins. Instead, consider changing the interaction that is passed to the functions as a means of having the caller provide different functionality.
 */
public class RunnerWrapperFactory {

    /*
      Call this to instantiate an object that you can use to execute gradle
      commands directly.

      Note: this function is meant to be backward and forward compatible. So
      this signature should not change at all, however, it may take and return
      objects that implement ADDITIONAL interfaces. That is, it will always
      return a GradleRunnerVersion1, but it may also be an object that implements
      GradleRunnerVersion2 (notice the 2). The caller will need to dynamically
      determine that. The GradleRunnerInteractionVersion1 may take an object
      that also implements GradleRunnerInteractionVersion2. If so, we'll
      dynamically determine that and handle it. Of course, this all depends on
      what happens in the future.

      @param  gradleHomeDirectory  the root directory of a gradle installation
      @param  interaction          this is how we interact with the caller.
      @param  showDebugInfo        true to show some additional information that
                                   may be helpful diagnosing problems is this
                                   fails
      @return a gradle runner
   */
    public static GradleRunnerVersion1 createGradleRunner(File gradleHomeDirectory, GradleRunnerInteractionVersion1 interaction, boolean showDebugInfo) throws Exception {
        return new GradleRunnerWrapper(gradleHomeDirectory, interaction);
    }
}
