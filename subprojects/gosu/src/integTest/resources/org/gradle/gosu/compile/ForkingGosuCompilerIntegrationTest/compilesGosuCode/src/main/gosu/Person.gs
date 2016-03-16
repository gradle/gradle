class Person {

  var _name : String
  var _age : Integer

  construct(name: String, age: Integer) {
    _name = name
    _age = age
  }

  override function toString() : String {
    return _name + ", " + _age
  }
}
