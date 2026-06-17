cmake_minimum_required(VERSION 3.22.1)

project("fixengine")

# إضافة ملف الـ C++ الخاص بنا
add_library(
        native-lib
        SHARED
        src/main/cpp/native-lib.cpp
)

# ربط مكتبات الأندرويد الأساسية (مثل مكتبة اللوغات للفحص)
find_library(
        log-lib
        log
)

target_link_libraries(
        native-lib
        ${log-lib}
)
