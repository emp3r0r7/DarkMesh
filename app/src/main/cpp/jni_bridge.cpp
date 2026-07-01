#include <jni.h>

#include <algorithm>
#include <vector>

namespace {

constexpr jint kMaxCompressInputBytes = 256;
constexpr jint kMaxDecompressInputBytes = 256;
constexpr jint kMaxDecompressedBytes = 512;

extern "C" int unishox2_compress(
    const char*,
    int,
    char*,
    int,
    const unsigned char[],
    const unsigned char[],
    const char*[],
    const char*[]
);

extern "C" int unishox2_decompress(
    const char*,
    int,
    char*,
    int,
    const unsigned char[],
    const unsigned char[],
    const char*[],
    const char*[]
);

constexpr unsigned char kUsxHcodesDefault[] = {0x00, 0x40, 0x80, 0xC0, 0xE0};
constexpr unsigned char kUsxHcodeLensDefault[] = {2, 2, 2, 3, 3};
const char* kUsxFreqSeqDefault[] = {"\": \"", "\": ", "</", "=\"", "\":\"", "://"};
const char* kUsxTemplates[] = {
    "tfff-of-tfTtf:rf:rf.fffZ",
    "tfff-of-tf",
    "(fff) fff-ffff",
    "tf:rf:rf",
    nullptr
};

jbyteArray makeByteArray(JNIEnv* env, const char* data, jint size) {
    auto result = env->NewByteArray(size);
    if (result == nullptr) return nullptr;

    env->SetByteArrayRegion(
        result,
        0,
        size,
        reinterpret_cast<const jbyte*>(data)
    );
    return result;
}

jbyteArray runUnishox(
    JNIEnv* env,
    jbyteArray input,
    bool compress,
    jint maxInputBytes,
    jint outputCapacity
) {
    if (input == nullptr) return nullptr;

    const jint inputSize = env->GetArrayLength(input);
    if (inputSize <= 0 || inputSize > maxInputBytes) return nullptr;

    std::vector<char> in(static_cast<size_t>(inputSize));
    env->GetByteArrayRegion(
        input,
        0,
        inputSize,
        reinterpret_cast<jbyte*>(in.data())
    );
    if (env->ExceptionCheck()) return nullptr;

    std::vector<char> out(static_cast<size_t>(outputCapacity));
    const int written = compress ? unishox2_compress(
        in.data(),
        inputSize,
        out.data(),
        outputCapacity,
        kUsxHcodesDefault,
        kUsxHcodeLensDefault,
        kUsxFreqSeqDefault,
        kUsxTemplates
    ) : unishox2_decompress(
        in.data(),
        inputSize,
        out.data(),
        outputCapacity,
        kUsxHcodesDefault,
        kUsxHcodeLensDefault,
        kUsxFreqSeqDefault,
        kUsxTemplates
    );
    if (written <= 0 || written > outputCapacity) return nullptr;

    return makeByteArray(env, out.data(), written);
}

} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_geeksville_mesh_util_NativeMessageCompression_isUnishoxAvailableNative(
    JNIEnv*,
    jobject
) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_geeksville_mesh_util_NativeMessageCompression_compressNative(
    JNIEnv* env,
    jobject,
    jbyteArray input
) {
    const jint inputSize = input == nullptr ? 0 : env->GetArrayLength(input);
    const jint outputCapacity = std::max<jint>(inputSize * 2 + 32, 64);
    return runUnishox(
        env,
        input,
        true,
        kMaxCompressInputBytes,
        outputCapacity
    );
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_geeksville_mesh_util_NativeMessageCompression_decompressNative(
    JNIEnv* env,
    jobject,
    jbyteArray input
) {
    return runUnishox(
        env,
        input,
        false,
        kMaxDecompressInputBytes,
        kMaxDecompressedBytes
    );
}
