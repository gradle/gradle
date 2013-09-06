package org.gradle.testing.cucumberjvm

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Timeout

class CucumberJVMReportTest extends AbstractIntegrationSpec {
    @Timeout(30)
    @Issue("http://issues.gradle.org/browse/GRADLE-2739")
    def "test writing CucumberJVM test result files when step defs contain literal forward slash"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies {
               testCompile "junit:junit:4.11"
               testCompile "info.cukes:cucumber-java:1.1.2"
               testCompile "info.cukes:cucumber-junit:1.1.2"
            }
            test {
               testLogging.showStandardStreams = true
               testLogging.events  'started', 'passed', 'skipped', 'failed', 'standardOut', 'standardError'
               reports.junitXml.enabled = false
               reports.html.enabled = false
            }
        """

        and:
        file("src/test/java/RunCukesTest.java") << """
           import cucumber.api.junit.Cucumber;
           import org.junit.runner.RunWith;

           @RunWith(Cucumber.class)
           public class RunCukesTest {}
        """

        and:
        file("src/test/java/HelloStepdefs.java") << """
        import cucumber.api.java.en.Given;
        import cucumber.api.java.en.Then;
        import cucumber.api.java.en.When;

        public class HelloStepdefs {
           @Given("^I have a hello app with Howdy and /four")
           public void I_have_a_hello_app_with() {
              System.out.println("Given");
           }

           @When("^I ask it to say hi and /five/six/seven")
           public void I_ask_it_to_say_hi() {
              System.out.println("When");
           }

           @Then("^it should answer with Howdy World")
           public void it_should_answer_with() {
              System.out.println("Then");
           }
        }
        """

        and:
        file("src/test/resources/helloworld.feature") << """
           Feature: Hello World /one

           @bar
           Scenario: Say hello /two/three
           Given I have a hello app with Howdy and /four
           When I ask it to say hi and /five/six/seven
           Then it should answer with Howdy World
        """

        when:
        run "test"

        then:
        ":test" in nonSkippedTasks
    }

}
