/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.agents;

import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Provides methods to interact with the Java agent shipped with Gradle. Because of the different class loaders, it is hard to query the Agent class directly.
 * <p>
 * The agent class must follow a special protocol to be recognized properly: the class should have a method:
 * <pre>
 *     public static boolean isApplied()
 * </pre>
 * that returns if the agent is applied, i.e. if one of its {@code premain} or {@code agentmain} entry methods was called.
 * <p>
 * It is possible to have an agent in the classpath without actually applying it, so checking for the availability of the class is not enough.
 */
public class AgentControl {
    private static final String INSTRUMENTATION_AGENT_CLASS_NAME = "org.gradle.instrumentation.agent.Agent";

    /**
     * Checks if the instrumentation agent class is applied to the current JVM.
     *
     * @return {@code true} if the agent was applied
     */
    public static boolean isInstrumentationAgentApplied() {
        return isAgentApplied(INSTRUMENTATION_AGENT_CLASS_NAME);
    }

    private static boolean isAgentApplied(String agentClassName) {
        try {
            // Java Agents are loaded by the system classloader.
            Class<?> agentClass = ClassLoader.getSystemClassLoader().loadClass(agentClassName);
            Method isAppliedMethod = agentClass.getMethod("isApplied");
            return (Boolean) isAppliedMethod.invoke(null);
        } catch (ClassNotFoundException e) {
            // This typically means that the agent is not loaded at all.
            // For now, this happens when running in a no-daemon mode, or when the Gradle distribution is not available.
            LoggerFactory.getLogger(AgentControl.class).debug("Agent {} is not loaded", agentClassName);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Agent class " + agentClassName + " doesn't provide public static method isApplied()");
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Failed to query status of agent " + agentClassName, e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Method " + agentClassName + ".isApplied() is not public", e);
        }
        return false;
    }
}
