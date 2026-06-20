package com.neonide.studio.app.home.create.template.cppactivity.jni

fun AndroidMkFile(): String = """
    LOCAL_PATH := $(call my-dir)

    include $(CLEAR_VARS)

    LOCAL_MODULE := tomaslib
    LOCAL_SRC_FILES := tomaslib.cpp
    LOCAL_LDLIBS := -llog

    include $(BUILD_SHARED_LIBRARY)
""".trimIndent()
