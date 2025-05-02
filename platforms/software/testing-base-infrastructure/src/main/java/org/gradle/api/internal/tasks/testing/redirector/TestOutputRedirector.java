/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.redirector;

import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.time.Clock;

public class TestOutputRedirector {
    private final StandardOutputRedirector redirector;
    Forwarder outForwarder;
    Forwarder errForwarder;

    public TestOutputRedirector(Clock clock, TestResultProcessor processor, StandardOutputRedirector redirector) {
        this.redirector = redirector;
        this.outForwarder = new Forwarder(clock, processor, TestOutputEvent.Destination.StdOut);
        this.errForwarder = new Forwarder(clock, processor, TestOutputEvent.Destination.StdErr);
    }

    public void startRedirecting() {
        assert outForwarder.outputOwner != null;
        assert errForwarder.outputOwner != null;

        redirector.redirectStandardOutputTo(outForwarder);
        redirector.redirectStandardErrorTo(errForwarder);
        redirector.start();
    }

    public void stopRedirecting() {
        redirector.stop();
    }

    public void setOutputOwner(Object testId) {
        assert testId != null;
        if (System.out != null) {
            System.out.flush();
        }
        if (System.err != null) {
            System.err.flush();
        }
        outForwarder.outputOwner = testId;
        errForwarder.outputOwner = testId;
    }

    static class Forwarder implements StandardOutputRedirector.OutputListener {
        final Clock clock;
        final TestResultProcessor processor;
        final TestOutputEvent.Destination dest;
        Object outputOwner;

        public Forwarder(Clock clock, TestResultProcessor processor, TestOutputEvent.Destination dest) {
            this.clock = clock;
            this.processor = processor;
            this.dest = dest;
        }

        @Override
        public void onOutput(CharSequence output) {
            if (outputOwner == null) {
                throw new RuntimeException("Unable send output event from test executor. Please report this problem. Destination: " + dest + ", event: " + output.toString());
            }
            processor.output(outputOwner, new DefaultTestOutputEvent(clock.getCurrentTime(), dest, output.toString()));
        }
    }
}
