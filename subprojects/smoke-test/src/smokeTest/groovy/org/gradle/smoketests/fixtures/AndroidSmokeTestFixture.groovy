/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.smoketests.fixtures

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.jcenterRepository

final class AndroidSmokeTestFixture {

    private AndroidSmokeTestFixture() {}

    static void assertAndroidHomeSet() {
        assert System.getenv().containsKey('ANDROID_HOME'): '''
            In order to run these tests the ANDROID_HOME directory must be set.
            It is not necessary to install the whole android SDK via Android Studio - it is enough if there is a $ANDROID_HOME/licenses/android-sdk-license containing the license keys from an Android Studio installation.
            The Gradle Android plugin will then download the SDK by itself, see https://developer.android.com/studio/intro/update.html#download-with-gradle
        '''.stripIndent()
    }

    static String buildscript(String androidPluginVersion) {
        """
            buildscript {
                ${googleRepository()}
                ${jcenterRepository()}

                dependencies {
                    classpath 'com.android.tools.build:gradle:${androidPluginVersion}'
                }
            }

            System.properties['com.android.build.gradle.overrideVersionCheck'] = 'true'
        """.stripIndent()
    }

    static String googleRepository() {
        """
            repositories {
                google()
            }
        """
    }

    static String activityDependency() {
        """
            dependencies {
                compile 'joda-time:joda-time:2.7'
            }
        """
    }

    static String androidPluginConfiguration(AndroidConfiguration androidConfiguration) {
        """
            android {
                compileSdkVersion $androidConfiguration.sdk.compileSdkVersion
                buildToolsVersion "${androidConfiguration.buildToolsVersion}"

                defaultConfig {
                    minSdkVersion $androidConfiguration.sdk.minSdkVersion
                    targetSdkVersion $androidConfiguration.sdk.targetSdkVersion
                    versionCode 1
                    versionName "1.0"
                }
                compileOptions {
                    sourceCompatibility JavaVersion.VERSION_1_7
                    targetCompatibility JavaVersion.VERSION_1_7
                }
                buildTypes {
                    release {
                        minifyEnabled false
                    }
                }
            }
        """.stripIndent()
    }

    static String unitTestSetup() {
        """
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """
    }

    static String instrumentedTestSetup() {
        """
            dependencies {
                androidTestImplementation 'com.android.support:support-annotations:26.1.0'
                androidTestImplementation 'com.android.support.test:runner:1.0.1'
            }
            
            android {
                defaultConfig {
                    testInstrumentationRunner 'android.support.test.runner.AndroidJUnitRunner'
                }
            }
        """
    }

    static String instrumentedTest() {
        """
            import org.junit.Test;
            import org.junit.runner.RunWith;
            import android.support.test.runner.AndroidJUnit4;
            import android.support.test.filters.SmallTest;

            @RunWith(AndroidJUnit4.class)
            @SmallTest
            public class SampleTest {
                @Test
                public void testSomething() {}
            }
        """
    }

    /**
     * Copied from <a href="https://github.com/googlesamples/android-testing/tree/master/unit/BasicSample">Google Samples</a>.
     */
    static String emailValidatorJavaSourceFile() {
        """
            import android.text.Editable;
            import android.text.TextWatcher;
            
            import java.util.regex.Pattern;

            public class EmailValidator implements TextWatcher {
            
                /**
                 * Email validation pattern.
                 */
                public static final Pattern EMAIL_PATTERN = Pattern.compile(
                        "[a-zA-Z0-9\\\\+\\\\.\\\\_\\\\%\\\\-\\\\+]{1,256}" +
                                "\\\\@" +
                                "[a-zA-Z0-9][a-zA-Z0-9\\\\-]{0,64}" +
                                "(" +
                                "\\\\." +
                                "[a-zA-Z0-9][a-zA-Z0-9\\\\-]{0,25}" +
                                ")+"
                );
            
                private boolean mIsValid = false;
            
                public boolean isValid() {
                    return mIsValid;
                }
            
                /**
                 * Validates if the given input is a valid email address.
                 *
                 * @param email        The email to validate.
                 * @return {@code true} if the input is a valid email. {@code false} otherwise.
                 */
                public static boolean isValidEmail(CharSequence email) {
                    return email != null && EMAIL_PATTERN.matcher(email).matches();
                }
            
                @Override
                final public void afterTextChanged(Editable editableText) {
                    mIsValid = isValidEmail(editableText);
                }
            
                @Override
                final public void beforeTextChanged(CharSequence s, int start, int count, int after) {/*No-op*/}
            
                @Override
                final public void onTextChanged(CharSequence s, int start, int before, int count) {/*No-op*/}
            }
        """
    }

    /**
     * Copied from <a href="https://github.com/googlesamples/android-testing/tree/master/unit/BasicSample">Google Samples</a>.
     */
    static String emailValidatorUnitTest() {
        """
            import org.junit.Test;

            import static org.junit.Assert.assertFalse;
            import static org.junit.Assert.assertTrue;

            public class EmailValidatorTest {
                @Test
                public void emailValidator_CorrectEmailSimple_ReturnsTrue() {
                    assertTrue(EmailValidator.isValidEmail("name@email.com"));
                }
            
                @Test
                public void emailValidator_CorrectEmailSubDomain_ReturnsTrue() {
                    assertTrue(EmailValidator.isValidEmail("name@email.co.uk"));
                }
            
                @Test
                public void emailValidator_InvalidEmailNoTld_ReturnsFalse() {
                    assertFalse(EmailValidator.isValidEmail("name@email"));
                }
            
                @Test
                public void emailValidator_InvalidEmailDoubleDot_ReturnsFalse() {
                    assertFalse(EmailValidator.isValidEmail("name@email..com"));
                }
            
                @Test
                public void emailValidator_InvalidEmailNoUsername_ReturnsFalse() {
                    assertFalse(EmailValidator.isValidEmail("@email.com"));
                }
            
                @Test
                public void emailValidator_EmptyString_ReturnsFalse() {
                    assertFalse(EmailValidator.isValidEmail(""));
                }
            
                @Test
                public void emailValidator_NullEmail_ReturnsFalse() {
                    assertFalse(EmailValidator.isValidEmail(null));
                }
            }
        """
    }
}
