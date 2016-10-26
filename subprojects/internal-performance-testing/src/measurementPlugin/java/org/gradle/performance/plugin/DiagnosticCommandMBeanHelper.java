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

package org.gradle.performance.plugin;

import javax.management.DynamicMBean;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import java.io.File;

class DiagnosticCommandMBeanHelper {

    public static String threadPrint() throws ReflectionException, MBeanException {
        return callDiagnosticsMethod("threadPrint");
    }

    public static String jfrDump(String recordingName, File recordingFile) throws ReflectionException, MBeanException {
        return callDiagnosticsMethod("jfrDump", "name=" + recordingName, "filename=" + recordingFile.getAbsolutePath());
    }

    private static String callDiagnosticsMethod(String actionName, String... args) throws MBeanException, ReflectionException {
        Object[] dcmdArgs = {args};
        String[] signature = {String[].class.getName()};
        DynamicMBean dcmd = getDiagnosticCommandMBean();
        return (String) dcmd.invoke(actionName, dcmdArgs, signature);
    }

    private static DynamicMBean getDiagnosticCommandMBean() {
        return DynamicMBean.class.cast(ReflectionUtil.invokeMethod(null, ReflectionUtil.findMethodByName(ReflectionUtil.loadClassIfAvailable("sun.management.ManagementFactoryHelper"), "getDiagnosticCommandMBean")));
    }
}
