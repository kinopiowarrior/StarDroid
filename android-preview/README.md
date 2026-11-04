# Build the Android HD2 preview

Start with the [official 1.5.4 source archive](https://github.com/kinopiowarrior/StarDroid/releases/download/v1.5.4/StarDroid-1.5.4-source.zip). Its SHA-256 is `df4cc9e6e7d5bd412f6d076f65518d761b1d26f5e765f79ba86bbbf86886c5a4`. The repository’s older application tree differs from that release. This directory provides the edits to its existing files. New files live at their normal paths on this branch.

Run the following from the draft branch checkout. Set `preview_src` to a fresh extraction of the 1.5.4 source.

```sh
preview_src=/path/to/StarDroid-1.5.4
patch_dir="$PWD/android-preview"
(cd "$preview_src" && git apply --check "$patch_dir/1.5.4-integration.patch" && git apply "$patch_dir/1.5.4-integration.patch")
tar -cf - -T "$patch_dir/copy-files.txt" | tar -C "$preview_src" -xf -
```

Follow `BUILD-1.5.4-hd2-preview3.txt` in the prepared tree to rebuild both native architectures and package the APK. The separately supplied preview source ZIP already includes these changes, rebuilt libraries and build manifests.

See [files and usage](../docs/HD2-ANDROID-PREVIEW.md) for the ZIP import and supported sprites.
