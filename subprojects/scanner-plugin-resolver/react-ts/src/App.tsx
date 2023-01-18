import { useState } from 'react'
import './App.css'

import data from '../../build/class-analysis.json' assert { type: "json" }



type ClassData = {
  name: string
  project: string
  dependencies: Array<string>
}

const getData = (): Array<ClassData> => {
    return data as unknown as Array<ClassData>
}

const App: React.FC<{}> = (props) => {
  
  const byModule: Map<string, Array<ClassData>>  = new Map();
  getData().forEach(clazz => {
    let bucket = byModule.get(clazz.project)
    if (!bucket) {
      byModule.set(clazz.project, [clazz])
    } else {
      bucket.push(clazz)
    }
  })


  const entries = Array.from(byModule.entries())
  entries.sort(([p1, classes1], [p2, classes2]) => classes2.length - classes1.length)

  return (
    <div>
      {entries.map(([project, classes]) => {
        return (<div key={project}>
            {project + " " + classes.length}
          </div>
        )
      })}
    </div>
  )

}

export default App
