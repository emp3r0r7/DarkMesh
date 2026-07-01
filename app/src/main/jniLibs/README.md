Prebuilt Unishox2 shared libraries:

- `arm64-v8a/libunishox2.so`
- `armeabi-v7a/libunishox2.so`
- `x86/libunishox2.so`
- `x86_64/libunishox2.so`

These libraries must be built with `UNISHOX_API_WITH_OUTPUT_LEN=1`.
The JNI bridge calls bounded `unishox2_compress(...)` and `unishox2_decompress(...)`,
not `*_simple(...)`.
