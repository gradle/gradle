class House {
  var _owner: Person as readonly Owner

  construct(owner: Person) {
    _owner = owner
  }
}
