LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libtermux-bootstrap
LOCAL_SRC_FILES := termux-bootstrap-zip.S termux-bootstrap.c
LOCAL_CFLAGS += -std=c11 -Wall -Wextra -Werror -Os -fno-stack-protector
LOCAL_LDFLAGS += -Wl,--gc-sections
include $(BUILD_SHARED_LIBRARY)

ifndef HEV_SOURCE_DIR
$(error HEV_SOURCE_DIR is required; build through Gradle to prepare pinned dependencies)
endif
include $(HEV_SOURCE_DIR)/Android.mk
