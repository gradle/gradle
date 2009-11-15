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

/**
 * @author Tom Eyckmans
 */
public class JUnit4Detecter {
    private static final JUnit4Detecter detecter = new JUnit4Detecter();

    private final boolean junit4;
    private JUnit4Detecter() {
        Class junit4TestAdapterClass = null;

        try {
            junit4TestAdapterClass = Class.forName("junit.framework.JUnit4TestAdapter");
        }
        catch (ClassNotFoundException noJunit4Exception) {
            // else JUnit 3
        }

        junit4 = junit4TestAdapterClass != null;
    }

    public static boolean isJUnit4Available()
    {
        return detecter.junit4;
    }
}
