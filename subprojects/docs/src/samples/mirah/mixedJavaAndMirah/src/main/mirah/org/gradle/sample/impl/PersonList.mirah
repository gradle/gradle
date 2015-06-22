package org.gradle.sample.impl

import org.gradle.sample.Person

class PersonList
	def find(name:String):Person
		MirahPerson.new(name)
	end
end
