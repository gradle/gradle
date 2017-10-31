import XCTest
@testable import Greeter

class GreeterTest: XCTestCase {
    func testEqualsExpectedMessage() {
        let g = Greeter()
        XCTAssertEqual("Hello, World!", g.getMessage())
    }
}
