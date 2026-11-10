package com.samiy.financemanager.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import com.samiy.financemanager.R;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.appcompat.view.WindowCallbackWrapper;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.RecyclerView;

public class KeyboardHelper {

    public static void hideKeyboard(View view) {
        if (view == null) return;
        if (view instanceof AutoCompleteTextView) {
            try {
                ((AutoCompleteTextView) view).dismissDropDown();
            } catch (Exception ignored) {}
        }
        InputMethodManager imm = (InputMethodManager) view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            if (view.getRootView() != null && view.getRootView().getWindowToken() != null) {
                imm.hideSoftInputFromWindow(view.getRootView().getWindowToken(), 0);
            }
        }
        view.clearFocus();
    }

    public static void hideKeyboard(Activity activity) {
        if (activity == null) return;
        View focus = activity.getCurrentFocus();
        if (focus != null) {
            hideKeyboard(focus);
        } else if (activity.getWindow() != null && activity.getWindow().getDecorView() != null) {
            hideKeyboard(activity.getWindow().getDecorView());
        }
    }

    public static void hideKeyboard(Dialog dialog) {
        if (dialog == null) return;
        View focus = dialog.getCurrentFocus();
        if (focus != null) {
            hideKeyboard(focus);
        } else if (dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
            hideKeyboard(dialog.getWindow().getDecorView());
        }
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

    /**
     * Configures an AutoCompleteTextView:
     * 1. Setting Done IME action to hide keyboard and clear focus.
     * 2. When an option is clicked from the dropdown, immediately hides keyboard and clears focus.
     * 3. Sets drop down height to WRAP_CONTENT so it only allocates as much space as required.
     */
    public static void setupAutoComplete(AutoCompleteTextView actv) {
        setupAutoComplete(actv, null, null, null);
    }

    public static void setupAutoComplete(AutoCompleteTextView actv, AdapterView.OnItemClickListener extraListener) {
        setupAutoComplete(actv, null, null, extraListener);
    }

    public static void setupAutoComplete(AutoCompleteTextView actv, View dropdownArrow, java.util.List<String> allItems, AdapterView.OnItemClickListener extraListener) {
        if (actv == null) return;
        setupDoneAction(actv);
        try {
            actv.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup);
            actv.setDropDownHeight(android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        } catch (Exception ignored) {}

        actv.setOnClickListener(v -> {
            if (!actv.isPopupShowing()) {
                if (allItems != null && actv.getText().toString().trim().isEmpty()) {
                    android.widget.ArrayAdapter<String> fresh = new android.widget.ArrayAdapter<>(
                            actv.getContext(), R.layout.item_autocomplete_dropdown, new java.util.ArrayList<>(allItems));
                    actv.setAdapter(fresh);
                }
                try {
                    actv.showDropDown();
                } catch (Exception ignored) {}
            }
        });

        if (dropdownArrow != null) {
            dropdownArrow.setOnClickListener(v -> {
                if (actv.isPopupShowing()) {
                    actv.dismissDropDown();
                } else {
                    if (allItems != null && actv.getText().toString().trim().isEmpty()) {
                        android.widget.ArrayAdapter<String> fresh = new android.widget.ArrayAdapter<>(
                                actv.getContext(), R.layout.item_autocomplete_dropdown, new java.util.ArrayList<>(allItems));
                        actv.setAdapter(fresh);
                    }
                    try {
                        actv.showDropDown();
                    } catch (Exception ignored) {}
                }
            });
        } else {
            // If no dedicated dropdown arrow View, allow tapping the right chevron area of the AutoCompleteTextView
            actv.setOnTouchListener((v, event) -> {
                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    int rightEdge = actv.getWidth() - actv.getPaddingEnd() - 48;
                    if (event.getX() >= rightEdge) {
                        if (actv.isPopupShowing()) {
                            actv.dismissDropDown();
                        } else {
                            if (allItems != null && actv.getText().toString().trim().isEmpty()) {
                                android.widget.ArrayAdapter<String> fresh = new android.widget.ArrayAdapter<>(
                                        actv.getContext(), R.layout.item_autocomplete_dropdown, new java.util.ArrayList<>(allItems));
                                actv.setAdapter(fresh);
                            }
                            try {
                                actv.showDropDown();
                            } catch (Exception ignored) {}
                        }
                        return true;
                    }
                }
                return false;
            });
        }

        actv.setOnItemClickListener((parent, view, position, id) -> {
            if (extraListener != null) {
                extraListener.onItemClick(parent, view, position, id);
            }
            hideKeyboard(actv);
        });
    }

    /**
     * Recursively attaches touch listeners to non-input views so that touching
     * anywhere outside an EditText dismisses the soft keyboard and clears focus.
     */
    public static void setupAutoDismissKeyboard(View root) {
        if (root == null) return;
        if (!(root instanceof EditText)) {
            root.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    hideKeyboard(v);
                }
                return false;
            });
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setupAutoDismissKeyboard(vg.getChildAt(i));
            }
        }
    }

    /**
     * Recursively attaches scroll listeners to scrollable containers (NestedScrollView,
     * ScrollView, RecyclerView) so that scrolling immediately dismisses the soft keyboard.
     */
    public static void setupScrollToDismissKeyboard(View root) {
        if (root == null) return;
        if (root instanceof NestedScrollView) {
            ((NestedScrollView) root).setOnScrollChangeListener(
                    (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> hideKeyboard(v)
            );
        } else if (root instanceof ScrollView) {
            root.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> hideKeyboard(v));
        } else if (root instanceof RecyclerView) {
            ((RecyclerView) root).addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        hideKeyboard(recyclerView);
                    }
                }
            });
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setupScrollToDismissKeyboard(vg.getChildAt(i));
            }
        }
    }

    /**
     * Intercepts touch events for any Dialog window to immediately dismiss
     * soft keyboard when tapping anywhere outside the currently focused EditText.
     */
    public static void setupOutsideTouchDismiss(Dialog dialog) {
        if (dialog == null || dialog.getWindow() == null) return;
        Window window = dialog.getWindow();
        Window.Callback originalCallback = window.getCallback();
        if (originalCallback == null) return;
        window.setCallback(new WindowCallbackWrapper(originalCallback) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    View currentFocus = dialog.getCurrentFocus();
                    if (currentFocus instanceof EditText) {
                        Rect outRect = new Rect();
                        currentFocus.getGlobalVisibleRect(outRect);
                        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                            currentFocus.clearFocus();
                            hideKeyboard(currentFocus);
                        }
                    }
                }
                return super.dispatchTouchEvent(event);
            }
        });
    }
}

