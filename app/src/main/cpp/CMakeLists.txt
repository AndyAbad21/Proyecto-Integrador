cmake_minimum_required(VERSION 3.22.1)

project("proyecto")

# Establecer el directorio de OpenCV (ajusta la ruta según tu configuración)
set(OpenCV_DIR /home/andy/Descargas/opencv-4.7.0-android-sdk/OpenCV-android-sdk/sdk/native/jni)

# Buscar OpenCV
find_package(OpenCV REQUIRED)

# Incluir las cabeceras de OpenCV
include_directories(${OpenCV_INCLUDE_DIRS})

# Crear la librería compartida
add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp
)

# Encontrar la librería JNI para gráficos
find_library(
        jnigraphics-lib jnigraphics
)

# Vincular bibliotecas
target_link_libraries(${CMAKE_PROJECT_NAME}
        ${OpenCV_LIBS}   # Bibliotecas de OpenCV
        ${jnigraphics-lib}  # JNI graphics library
        ${log-lib}         # Log library
        android            # Android system library
        log                 # Logging library for debugging
)
