cmake_minimum_required(VERSION 3.22.1)
project("qingjian_native" LANGUAGES C CXX)

# C++ 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# 查找 Android 日志库 (用于 __android_log_print)
find_library(log-lib log)

# 源文件
add_library(
    qingjian_native
    SHARED
    qingjian_native.cpp
)

# 链接日志库
target_link_libraries(
    qingjian_native
    ${log-lib}
)

# 编译选项: -O2 优化 + -fvisibility=hidden 隐藏符号
target_compile_options(qingjian_native PRIVATE -O2 -fvisibility=hidden)
