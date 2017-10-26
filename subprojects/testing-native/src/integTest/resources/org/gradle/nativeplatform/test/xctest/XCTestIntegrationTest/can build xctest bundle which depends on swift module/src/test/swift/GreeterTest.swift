import XCTest
@testable import Greeter

class GreeterTest: XCTestCase {
    func testEqualsExpectedMessage() {
        XCTAssertEqual("Hello, World!", getMessage())
    }
}
