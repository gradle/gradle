/*
 * Copyright 2013 the original author or authors.
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

package org.gradle;

import org.junit.runner.Runner;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Locales extends Suite {
    private static final Iterable<Locale> localesToUse = Arrays.asList(Locale.FRENCH, Locale.GERMAN, Locale.ENGLISH);

    public Locales(Class<?> klass) throws InitializationError {
        super(klass, extracAndCreateRunners(klass));
    }

    private static List<Runner> extracAndCreateRunners(Class<?> klass) throws InitializationError {
        List<Runner> runners = new ArrayList<Runner>();
        for (Locale locale : localesToUse) {
            runners.add(new LocalesRunner(locale, klass));
        }
        return runners;
    }

    private static class LocalesRunner extends BlockJUnit4ClassRunner {
        private final Locale locale;

        LocalesRunner(Locale locale, Class<?> klass) throws InitializationError {
            super(klass);
            this.locale = locale;
        }

        @Override
        protected Statement methodBlock(final FrameworkMethod method) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    Locale oldLocale = LocaleHolder.set(locale);
                    try {
                        LocalesRunner.super.methodBlock(method).evaluate();
                    } finally {
                        LocaleHolder.set(oldLocale);
                    }
                }
            };
        }

        @Override// The name of the test class
        protected String getName() {
            return String.format("%s [%s]", super.getName(), locale);
        }

        @Override// The name of the test method
        protected String testName(final FrameworkMethod method) {
            return String.format("%s [%s]", method.getName(), locale);
        }
    }

}
