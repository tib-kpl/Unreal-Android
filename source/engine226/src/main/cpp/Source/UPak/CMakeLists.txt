project(UPak CXX)

set(SRC_FILES
  "Src/UPak.cpp"
)

add_library(${PROJECT_NAME} ${LIB_TYPE} ${SRC_FILES})

target_include_directories(${PROJECT_NAME}
  PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/Src
  # UnNativeLayoutMacros.h
  ${CMAKE_CURRENT_SOURCE_DIR}/../Engine/Src
)

target_link_libraries(${PROJECT_NAME} Engine Core)

target_compile_definitions(${PROJECT_NAME} PRIVATE UPAK_EXPORTS UPACKAGE_NAME=${PROJECT_NAME})
