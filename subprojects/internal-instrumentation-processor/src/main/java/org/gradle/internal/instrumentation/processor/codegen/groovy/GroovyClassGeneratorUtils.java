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

package org.gradle.internal.instrumentation.processor.codegen.groovy;

import com.squareup.javapoet.ClassName;
import org.gradle.internal.instrumentation.model.CallInterceptionRequest;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.internal.instrumentation.model.RequestExtra;
import org.gradle.internal.instrumentation.util.NameUtil;
import org.gradle.util.internal.TextUtil;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class GroovyClassGeneratorUtils {

    private static GroupedCallInterceptionRequests groupRequests(Collection<CallInterceptionRequest> interceptionRequests) {
        Map<String, List<CallInterceptionRequest>> namedRequests = new LinkedHashMap<>();
        Map<Type, List<CallInterceptionRequest>> constructorRequests = new LinkedHashMap<>();
        interceptionRequests.forEach(request -> {
            if (request.getRequestExtras().getByType(RequestExtra.InterceptGroovyCalls.class).isPresent()) {
                CallableInfo callable = request.getInterceptedCallable();
                CallableKindInfo kind = callable.getKind();
                if (kind == CallableKindInfo.AFTER_CONSTRUCTOR) {
                    constructorRequests.computeIfAbsent(request.getInterceptedCallable().getOwner().getType(), key -> new ArrayList<>()).add(request);
                } else {
                    String nameKey = NameUtil.interceptedJvmMethodName(callable);
                    namedRequests.computeIfAbsent(nameKey, key -> new ArrayList<>()).add(request);
                }
            }
        });

        return new GroupedCallInterceptionRequests(namedRequests, constructorRequests);
    }

    static class GroupedCallInterceptionRequests {
        private final Map<String, List<CallInterceptionRequest>> namedRequests = new LinkedHashMap<>();
        private final Map<Type, List<CallInterceptionRequest>> constructorRequests = new LinkedHashMap<>();


        public Map<String, List<CallInterceptionRequest>> getNamedRequests() {
            return namedRequests;
        }

        public Map<Type, List<CallInterceptionRequest>> getConstructorRequests() {
            return constructorRequests;
        }
    }

    static class CallInterceptingRequestGroup {
        private final String className;
        private final List<CallInterceptionRequest> requests;

        private CallInterceptingRequestGroup(String className, List<CallInterceptionRequest> requests) {
            this.className = className;
            this.requests = requests;
        }

        static CallInterceptingRequestGroup namedCallableInterceptorGroup(String name, List<CallInterceptionRequest> requests) {
            String className = TextUtil.capitalize(name) + "CallInterceptor";
            return new CallInterceptingRequestGroup(name, className, requests);
        }

        static CallInterceptingRequestGroup constructorInterceptorGroup(Type constructorType, List<CallInterceptionRequest> requests) {
            String className = ClassName.bestGuess(constructorType.getClassName()).simpleName() + "ConstructorCallInterceptor";
            return new CallInterceptingRequestGroup(className, requests);
        }

        public String getClassName() {
            return className;
        }
        public List<CallInterceptionRequest> getRequests() {
            return requests;
        }
    }
}
