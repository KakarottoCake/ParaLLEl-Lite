#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "libbps.h"

// Helper function to read file into memory
static bool load_file(const char *path, struct mem *m) {
    m->ptr = NULL;
    m->len = 0;
    FILE *f = fopen(path, "rb");
    if (!f) return false;
    
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    if (size < 0) {
        fclose(f);
        return false;
    }
    fseek(f, 0, SEEK_SET);
    
    m->ptr = (uint8_t *)malloc(size);
    if (!m->ptr) {
        fclose(f);
        return false;
    }
    
    size_t read_bytes = fread(m->ptr, 1, size, f);
    fclose(f);
    
    if (read_bytes != (size_t)size) {
        free(m->ptr);
        m->ptr = NULL;
        return false;
    }
    
    m->len = size;
    return true;
}

// Helper function to write memory to file
static bool save_file(const char *path, struct mem m) {
    FILE *f = fopen(path, "wb");
    if (!f) return false;
    
    size_t written_bytes = fwrite(m.ptr, 1, m.len, f);
    fclose(f);
    
    return written_bytes == m.len;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_patching_PatchingEngine_applyBpsPatch(
    JNIEnv *env,
    jobject obj,
    jstring jPatchPath,
    jstring jInputRomPath,
    jstring jOutputRomPath
) {
    const char *patch_path = env->GetStringUTFChars(jPatchPath, NULL);
    const char *input_path = env->GetStringUTFChars(jInputRomPath, NULL);
    const char *output_path = env->GetStringUTFChars(jOutputRomPath, NULL);
    
    struct mem patch = { NULL, 0 };
    struct mem input = { NULL, 0 };
    struct mem output = { NULL, 0 };
    
    jint result = bps_ok;
    
    if (!load_file(patch_path, &patch)) {
        result = bps_io;
        goto cleanup;
    }
    
    if (!load_file(input_path, &input)) {
        result = bps_io;
        goto cleanup;
    }
    
    // Call flips apply bps patch
    result = bps_apply(patch, input, &output, NULL, false);
    
    if (result == bps_ok) {
        if (!save_file(output_path, output)) {
            result = bps_io;
        }
    }
    
cleanup:
    if (patch.ptr) free(patch.ptr);
    if (input.ptr) free(input.ptr);
    if (output.ptr) free(output.ptr);
    
    env->ReleaseStringUTFChars(jPatchPath, patch_path);
    env->ReleaseStringUTFChars(jInputRomPath, input_path);
    env->ReleaseStringUTFChars(jOutputRomPath, output_path);
    
    return result;
}
