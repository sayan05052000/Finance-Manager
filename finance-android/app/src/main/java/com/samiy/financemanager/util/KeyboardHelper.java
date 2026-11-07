package com.samiy.financemanager.util;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

public class KeyboardHelper {

    public static void hideKeyboard(View view) {
        if (view == null) return;
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
        view.clearFocus();
    }

    /**
     * Binds an IME Action listener to an EditText so that when the user taps the
     * tick / checkmark / Done button (IME_ACTION_DONE, IME_ACTION_GO, IME_ACTION_NEXT, or Enter),
     * the soft keyboard is immediately dismissed and focus is cleared.
     */
    public static void setupDoneAction(EditText editText) {
        if (editText == null) return;
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard(v);
            return true;
        });
    }

    public static void setupDoneAction(EditText... editTexts) {
        if (editTexts == null) return;
        for (EditText et : editTexts) {
            setupDoneAction(et);
        }
    }
}
