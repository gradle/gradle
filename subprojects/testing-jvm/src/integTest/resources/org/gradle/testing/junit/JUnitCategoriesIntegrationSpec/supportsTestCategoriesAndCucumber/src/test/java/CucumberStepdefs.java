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
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class CucumberStepdefs {

    @Given("^a project containing cucumber tests$")
    public void aProjectContainingCucumberTests() throws Throwable {
        System.out.println("Given");
    }

    @When("^restricting junit tests using categories$")
    public void restrictingJunitTestsUsingCategories() throws Throwable {
        System.out.println("When");
    }

    @Then("^the test should not fail to initialize with an NullPointerException$")
    public void theTestShouldNotFailToInitializeWithAnNullPointerException() throws Throwable {
        System.out.println("Then");
    }
}
