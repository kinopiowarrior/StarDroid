package org.openbw.androidtest;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.Arrays;
import java.util.HashSet;

/** Physical desktop input policy; UI routing and native calls remain in NativeSurface. */
final class DesktopControls {
  static final int BUTTONS = MotionEvent.BUTTON_PRIMARY | MotionEvent.BUTTON_SECONDARY
      | MotionEvent.BUTTON_TERTIARY;
  int buttons;
  private final HashSet<Integer> heldKeys = new HashSet<Integer>();

  /** Cursor 3 is ordinary unit hover; it must still receive context orders. */
  static boolean targetingCursor(int cursor) { return cursor == 1 || cursor == 2 || cursor == 4; }

  static boolean mouseSource(int source) {
    return (source & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
  }

  private static boolean controllerSource(int source) {
    return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        || (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
  }

  static boolean keyboardSource(int source) {
    return (source & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
        && !controllerSource(source) && !mouseSource(source);
  }

  static boolean keyboard(KeyEvent event) {
    if (controllerSource(event.getSource()) || mouseSource(event.getSource())) return false;
    InputDevice device = event.getDevice();
    if (device != null && controllerSource(device.getSources())
        && (cameraKey(event.getKeyCode()) || KeyEvent.isGamepadButton(event.getKeyCode()))) return false;
    return keyboardSource(event.getSource()) || event.getSource() == InputDevice.SOURCE_UNKNOWN
        && device != null && device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC;
  }

  /** A mouse-generated Back must not also close the game menu after a right click. */
  static boolean mouseBack(KeyEvent event) {
    if (event.getKeyCode() != KeyEvent.KEYCODE_BACK) return false;
    if (mouseSource(event.getSource())) return true;
    InputDevice device = event.getDevice();
    return device != null && mouseSource(device.getSources())
        && (event.getSource() == InputDevice.SOURCE_UNKNOWN
            || device.getKeyboardType() != InputDevice.KEYBOARD_TYPE_ALPHABETIC);
  }

  /** A-Z use physical Android key codes, so caps/Shift or a Russian layout do not change commands. */
  static int symbol(int key, int unicode) {
    if (key >= KeyEvent.KEYCODE_A && key <= KeyEvent.KEYCODE_Z) return 'a' + key - KeyEvent.KEYCODE_A;
    if (key >= KeyEvent.KEYCODE_0 && key <= KeyEvent.KEYCODE_9) return '0' + key - KeyEvent.KEYCODE_0;
    if (key >= KeyEvent.KEYCODE_NUMPAD_0 && key <= KeyEvent.KEYCODE_NUMPAD_9)
      return '0' + key - KeyEvent.KEYCODE_NUMPAD_0;
    switch (key) {
      case KeyEvent.KEYCODE_ESCAPE: return 27;
      case KeyEvent.KEYCODE_TAB: return 9;
      case KeyEvent.KEYCODE_SPACE: return 32;
      case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER: return 13;
      case KeyEvent.KEYCODE_DEL: return 8;
      default: return !modifierKey(key) && !cameraKey(key) && unicode >= 32 && unicode < 127
          ? Character.toLowerCase((char) unicode) : 0;
    }
  }

  /** SDL/USB scancodes used by the imported engine, not Android/Linux scan codes. */
  static int scanCode(int key) {
    if (key >= KeyEvent.KEYCODE_A && key <= KeyEvent.KEYCODE_Z) return 4 + key - KeyEvent.KEYCODE_A;
    if (key >= KeyEvent.KEYCODE_1 && key <= KeyEvent.KEYCODE_9) return 30 + key - KeyEvent.KEYCODE_1;
    switch (key) {
      case KeyEvent.KEYCODE_0: return 39;
      case KeyEvent.KEYCODE_ENTER: return 40;
      case KeyEvent.KEYCODE_ESCAPE: return 41;
      case KeyEvent.KEYCODE_DEL: return 42;
      case KeyEvent.KEYCODE_TAB: return 43;
      case KeyEvent.KEYCODE_SPACE: return 44;
      case KeyEvent.KEYCODE_CTRL_LEFT: return 224;
      case KeyEvent.KEYCODE_SHIFT_LEFT: return 225;
      case KeyEvent.KEYCODE_ALT_LEFT: return 226;
      case KeyEvent.KEYCODE_CTRL_RIGHT: return 228;
      case KeyEvent.KEYCODE_SHIFT_RIGHT: return 229;
      case KeyEvent.KEYCODE_ALT_RIGHT: return 230;
      default: return 0;
    }
  }

  static boolean cameraKey(int key) {
    return key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
        || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN;
  }

  static boolean modifierKey(int key) {
    return key == KeyEvent.KEYCODE_CTRL_LEFT || key == KeyEvent.KEYCODE_CTRL_RIGHT
        || key == KeyEvent.KEYCODE_SHIFT_LEFT || key == KeyEvent.KEYCODE_SHIFT_RIGHT
        || key == KeyEvent.KEYCODE_ALT_LEFT || key == KeyEvent.KEYCODE_ALT_RIGHT;
  }

  /** True only for a fresh edge. OS repeats never retrigger orders or group assignment. */
  boolean key(int key, boolean down, int repeatCount) {
    return down ? repeatCount == 0 && heldKeys.add(key) : heldKeys.remove(key);
  }

  boolean held(int key) { return heldKeys.contains(key); }
  boolean shift() { return held(KeyEvent.KEYCODE_SHIFT_LEFT) || held(KeyEvent.KEYCODE_SHIFT_RIGHT); }
  boolean ctrl() { return held(KeyEvent.KEYCODE_CTRL_LEFT) || held(KeyEvent.KEYCODE_CTRL_RIGHT); }
  int cameraX() { return (held(KeyEvent.KEYCODE_DPAD_RIGHT) ? 1 : 0) - (held(KeyEvent.KEYCODE_DPAD_LEFT) ? 1 : 0); }
  int cameraY() { return (held(KeyEvent.KEYCODE_DPAD_DOWN) ? 1 : 0) - (held(KeyEvent.KEYCODE_DPAD_UP) ? 1 : 0); }

  /** Caller sends native key-up for these keys before forgetting focus/device state. */
  int[] releaseKeys() {
    int[] keys = new int[heldKeys.size()]; int i = 0;
    for (int key : heldKeys) keys[i++] = key;
    Arrays.sort(keys); heldKeys.clear(); return keys;
  }

  /**
   * Returns changed button bits; buttons contains the new primary/secondary/tertiary state.
   * Android delivers generic BUTTON_PRESS/RELEASE as well as DOWN/UP for the same click.
   * Callers derive presses as changed & buttons, releases as changed & ~buttons.
   */
  int updateButtons(int action, int buttonState, int actionButton) {
    int before = buttons, state = buttonState & BUTTONS;
    switch (action) {
      case MotionEvent.ACTION_CANCEL: buttons = 0; break;
      case MotionEvent.ACTION_BUTTON_PRESS: buttons = state | (actionButton & BUTTONS); break;
      case MotionEvent.ACTION_BUTTON_RELEASE: buttons = state & ~(actionButton & BUTTONS); break;
      case MotionEvent.ACTION_DOWN: buttons = state != 0 ? state : buttons != 0 ? buttons : MotionEvent.BUTTON_PRIMARY; break;
      case MotionEvent.ACTION_UP: case MotionEvent.ACTION_MOVE:
      case MotionEvent.ACTION_HOVER_MOVE: case MotionEvent.ACTION_HOVER_ENTER: buttons = state; break;
      // HOVER_EXIT also precedes a press; it is not a release or cancellation.
      default: break;
    }
    return before ^ buttons;
  }

  int releaseButtons() { int previous = buttons; buttons = 0; return previous; }
}
