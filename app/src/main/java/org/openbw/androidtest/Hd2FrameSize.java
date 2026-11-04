package org.openbw.androidtest;

/** Render density does not change the logical viewport or touch coordinates. */
final class Hd2FrameSize {
  private Hd2FrameSize() {}

  // At most 64 MiB for the final ARGB bitmap. This covers the complete normal
  // zoom range, including the app's widest 3200 x 960 logical viewport.
  static int scale(boolean enabled, int width, int height, int maxBitmapWidth, int maxBitmapHeight) {
    if (!enabled || width <= 0 || height <= 0 || width > maxBitmapWidth / 2 || height > maxBitmapHeight / 2) return 1;
    return (long) width * height <= 4L * 1024 * 1024 ? 2 : 1;
  }
}
