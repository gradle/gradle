importar sqlite3

# Conexión a la base de datos (se creará si no existe)
conn = sqlite3.connect( " datos_salud.db " )
cursor = conn.cursor()

# Crear tabla para almacenar datos de salud
cursor.ejecutar( ' ' '
CREAR TABLA SI NO EXISTE salud (
    id ENTERO CLAVE PRIMARIA AUTOINCREMENTO,
    nombre TEXTO NO NULO,
    edad ENTERO NO NULO,
    peso REAL NO NULO,
    altura REAL NO NULO,
    presion_arterial TEXTO NO NULO
)
' ' ' )

print ( " Tabla creada exitosamente. " )

# Función para agregar datos
def agregar_datos (nombre, edad, peso, altura, presion_arterial):
    cursor.ejecutar( ' ' '
    INSERT INTO salud (nombre, edad, peso, altura, presion_arterial)
    VALORES (?, ?, ?, ?, ?)
    ' ' ' , (nombre, edad, peso, altura, presion_arterial))
    conn.commit()
    print( " Datos agregados correctamente. " )

# Función para mostrar todos los datos
def mostrar_datos ():
    cursor.execute( ' SELECT * FROM salud ' )
    registros = cursor.fetchall()
    para registrar en registros:
        imprimir(registro)

# Ejemplo de uso
agregar_datos ( " Juan Pérez " , 35 , 70.5 , 1.75 , " 120/80 " )
agregar_datos( " Ana López " , 28 , 65.0 , 1.68 , " 110/70 " )

print( " \n Datos almacenados: " )
mostrar_datos()

#Cerrar la conexión
conn.cerrar()
