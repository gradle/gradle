/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.tooling.r22;

import org.gradle.api.GradleException;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

import java.io.File;
import java.net.URI;

public class CancelledInControllerBuildAction implements BuildAction<Void> {
    private final URI markerURI;

    public CancelledInControllerBuildAction(URI markerURI) {
        this.markerURI = markerURI;
    }

    public Void execute(BuildController controller) {
        System.out.println("waiting");
        File marker = new File(markerURI);
        long timeout = System.currentTimeMillis() + 10000L;
        while (!marker.exists() && System.currentTimeMillis() < timeout) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while waiting for marker file", e);
            }
        }
        if (!marker.exists()) {
            throw new RuntimeException("Timeout waiting for marker file " + markerURI);
        }
        controller.getBuildModel();
        System.out.println("finished");
        throw new GradleException("Should be cancelled before the end of action.");
    }

}
