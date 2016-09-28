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

package org.gradle.launcher.daemon.server.health.memory;

import org.gradle.internal.Cast;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.lang.management.ManagementFactory;

public class MBeanAttributeProvider {
    /**
     * Calls an mbean method if available.
     *
     * @throws UnsupportedOperationException if this method isn't available on this JVM.
     */
    public static <T> T getMbeanAttribute(String mbean, final String attribute, Class<T> type) {
        Exception rootCause;
        try {
            ObjectName objectName = new ObjectName(mbean);
            return Cast.cast(type, ManagementFactory.getPlatformMBeanServer().getAttribute(objectName, attribute));
        } catch (InstanceNotFoundException e) {
            rootCause = e;
        } catch (ReflectionException e) {
            rootCause = e;
        } catch (MalformedObjectNameException e) {
            rootCause = e;
        } catch (MBeanException e) {
            rootCause = e;
        } catch (AttributeNotFoundException e) {
            rootCause = e;
        }
        throw new UnsupportedOperationException("(" + mbean + ")." + attribute + " is unsupported on this JVM.", rootCause);
    }
}
