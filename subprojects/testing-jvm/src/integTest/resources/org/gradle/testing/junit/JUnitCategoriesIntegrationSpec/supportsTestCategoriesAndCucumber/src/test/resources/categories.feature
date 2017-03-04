Feature: Junit Categories

    Scenario: Using Categories and Cucumber in the same project
        Given a project containing cucumber tests
        When restricting junit tests using categories
        Then the test should not fail to initialize with an NullPointerException
