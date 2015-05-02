class Person
  attr_reader name:String
  attr_reader age:int
  
  def initialize(name:String,age:int)
    @name = name
    @age  = age
  end
end

