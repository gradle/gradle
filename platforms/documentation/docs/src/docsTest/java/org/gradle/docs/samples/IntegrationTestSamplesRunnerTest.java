/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.docs.samples;

import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.JUnit4;
import org.junit.runners.model.InitializationError;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(JUnit4.class)
public class IntegrationTestSamplesRunnerTest {
    @Test
    public void canAddReproductionInstructionsUponFailure() throws InitializationError {
        IntegrationTestSamplesRunner runner = new IntegrationTestSamplesRunner(Sample.class);
        RunNotifier notifier = new RunNotifier();
        List<Failure> capturedFailures = new ArrayList<>();
        Sample errorThrowingSample = new Sample("test-sample", new File(""), emptyList()) {
            @Override
            public List<Command> getCommands() {
                throw new RuntimeException("I am a test failure!");
            }
        };

        notifier.addListener(new RunListener() {
            @Override
            public void testFailure(Failure failure) throws Exception {
                capturedFailures.add(failure);
            }
        });

        runner.runChild(errorThrowingSample, notifier);
        assertEquals(1, capturedFailures.size());
        assertTrue(capturedFailures.get(0).getException().getMessage().contains("To reproduce this failure, run:\n  ./gradlew docs:docsTest --tests '*test-sample*' "));
    }
}
