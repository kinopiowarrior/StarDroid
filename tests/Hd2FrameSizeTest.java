package org.openbw.androidtest;

/** Exercises the exact Android output-density gate without requiring a device. */
public final class Hd2FrameSizeTest {
  private static int checks;
  private static void expect(int want, boolean enabled, int w, int h, int limitWidth, int limitHeight) {
    int actual = Hd2FrameSize.scale(enabled, w, h, limitWidth, limitHeight);
    if (want != actual) throw new AssertionError("Unexpected density for " + w + "x" + h + ": " + actual);
    ++checks;
  }
  public static void main(String[] ignored) {
    // Base, reference phone landscape, zoomed-in, and widest supported zoom-out.
    for (int[] v : new int[][] {{640,480}, {1040,480}, {427,240}, {3200,960}}) {
      expect(2, true, v[0], v[1], 8192, 8192);
      expect(1, false, v[0], v[1], 8192, 8192);
    }
    expect(1, true, 1025, 768, 2048, 2048); // width limit
    expect(1, true, 640, 1025, 2048, 2048); // height limit
    expect(2, true, 2048, 2048, 4096, 4096); // exact bitmap and pixel limit
    expect(1, true, 2049, 2048, 8192, 8192); // one column over memory budget
    expect(1, true, 0, 480, 4096, 4096);
    expect(1, true, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    System.out.println("PASS " + checks + " HD2 output-density checks");
  }
}
