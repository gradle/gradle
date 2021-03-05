import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

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
