// The bridge between Kotlin (data/stt/Whisper.kt) and whisper.cpp: load a
// model, transcribe 16 kHz audio in Arabic, free the model. Nothing else.
#include <jni.h>
#include <string>
#include <thread>
#include "whisper.h"

#define EXPORT extern "C" JNIEXPORT __attribute__((visibility("default")))

EXPORT jlong JNICALL Java_org_mushaf_app_data_stt_Whisper_load(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(p, params);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

EXPORT jstring JNICALL Java_org_mushaf_app_data_stt_Whisper_transcribe(JNIEnv *env, jobject, jlong handle, jfloatArray audio) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr || audio == nullptr) return env->NewStringUTF("");
    jsize n = env->GetArrayLength(audio);
    // At most 30 s: what Whisper hears in one window.
    if (n <= 0 || n > 16000 * 30) return env->NewStringUTF("");
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "ar";
    params.translate = false;
    params.no_timestamps = true;
    params.single_segment = true;
    params.no_context = true;
    params.suppress_blank = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;
    params.n_threads = std::max(1, std::min(4, (int) std::thread::hardware_concurrency()));
    std::string text;
    if (whisper_full(ctx, params, samples, n) == 0) {
        int segments = whisper_full_n_segments(ctx);
        for (int i = 0; i < segments; i++) text += whisper_full_get_segment_text(ctx, i);
    }
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    return env->NewStringUTF(text.c_str());
}

EXPORT void JNICALL Java_org_mushaf_app_data_stt_Whisper_free(JNIEnv *, jobject, jlong handle) {
    auto *ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) whisper_free(ctx);
}
