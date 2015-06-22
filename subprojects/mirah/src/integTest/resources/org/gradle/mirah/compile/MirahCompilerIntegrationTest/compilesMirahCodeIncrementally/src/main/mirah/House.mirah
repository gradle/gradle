class House
  attr_reader owner:Person
  
  def initialize(owner:Person)
    @owner = owner
  end
end
