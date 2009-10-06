/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.external.junit;

import org.gradle.api.testing.fabric.TestProcessResultFactory;
import org.gradle.api.testing.fabric.TestProcessor;
import org.gradle.api.testing.fabric.TestProcessorFactory;

/**
 * @author Tom Eyckmans
 */
public class JUnitTestProcessorFactory implements TestProcessorFactory {

    private ClassLoader sandboxClassLoader;
    private TestProcessResultFactory testProcessResultFactory;
    private Class junit4TestAdapterClass;
    private boolean junit4;

    public void initialize(ClassLoader sandboxClassLoader, TestProcessResultFactory testProcessResultFactory) {
        this.sandboxClassLoader = sandboxClassLoader;
        this.testProcessResultFactory = testProcessResultFactory;

        Class junit4TestAdapterClassCandidate = null;
        try {
            junit4TestAdapterClassCandidate = Class.forName("junit.framework.JUnit4TestAdapter");
        }
        catch (ClassNotFoundException noJunit4Exception) {
            // else JUnit 3
        }
        finally {
            if (junit4TestAdapterClassCandidate == null)
                junit4TestAdapterClass = null;
            else
                junit4TestAdapterClass = junit4TestAdapterClassCandidate;
        }
        junit4 = junit4TestAdapterClass != null;
    }

    public TestProcessor createProcessor() {
        return new JUnitTestProcessor(sandboxClassLoader, testProcessResultFactory, junit4TestAdapterClass, junit4);
    }
}
