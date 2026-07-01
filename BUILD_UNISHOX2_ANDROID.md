# Build Unishox2 for Android

Clone source:

```powershell
git clone https://github.com/siara-cc/Unishox2 third_party/Unishox2
```

Build all Android ABIs:

```powershell
.\build-unishox2-android.ps1
```

On Linux/macOS/WSL:

```bash
./build-unishox2-android.sh
```

The script compiles with:

```text
-DUNISHOX_API_WITH_OUTPUT_LEN=1
-Wl,-z,max-page-size=16384
-Wl,-soname,libunishox2.so
```

Reason: DarkMesh JNI calls bounded `unishox2_compress(...)` and `unishox2_decompress(...)`, not `*_simple(...)`.
`unishox2_decompress_simple(...)` uses an effectively unlimited output length and must not be used here.
The linker page-size flag keeps Android 15+ 16 KB page-size devices compatible.
The SONAME flag prevents dependent JNI libraries from embedding a build-machine absolute path.

Output:

```text
app/src/main/jniLibs/arm64-v8a/libunishox2.so
app/src/main/jniLibs/armeabi-v7a/libunishox2.so
app/src/main/jniLibs/x86/libunishox2.so
app/src/main/jniLibs/x86_64/libunishox2.so
```

Options:

```powershell
.\build-unishox2-android.ps1 -Unishox2Dir third_party/Unishox2 -Api 24 -NdkVersion 28.2.13676358
```

Alternative output directory:

```powershell
.\build-unishox2-android.ps1 -OutDir build/unishox2-jniLibs
```

```bash
./build-unishox2-android.sh --out-dir build/unishox2-jniLibs
```
