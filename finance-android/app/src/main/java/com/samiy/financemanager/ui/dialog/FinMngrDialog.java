package com.samiy.financemanager.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.samiy.financemanager.R;

public class FinMngrDialog {

    public interface OnConfirmListener {
        void onConfirm();
    }

    public interface OnChoiceSelectedListener {
        void onSelected(String selected);
    }

    public static Dialog showSingleChoice(Context context, String title, String[] items, String currentSelection, OnChoiceSelectedListener onSelected) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View v = LayoutInflater.from(context).inflate(R.layout.dialog_finmngr_single_choice, null);
        dialog.setContentView(v);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = v.findViewById(R.id.tvChoiceTitle);
        android.widget.ListView lvList = v.findViewById(R.id.lvChoiceList);
        MaterialButton btnCancel = v.findViewById(R.id.btnChoiceCancel);

        tvTitle.setText(title);

        int checkedIndex = -1;
        if (currentSelection != null) {
            for (int i = 0; i < items.length; i++) {
                if (items[i].equalsIgnoreCase(currentSelection.trim())) {
                    checkedIndex = i;
                    break;
                }
            }
        }

        final int selectedPos = checkedIndex;
        android.widget.BaseAdapter adapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return items.length;
            }

            @Override
            public Object getItem(int position) {
                return items[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(context).inflate(R.layout.item_dialog_single_choice, parent, false);
                }
                android.widget.RadioButton rb = convertView.findViewById(R.id.rbChoiceItem);
                TextView tv = convertView.findViewById(R.id.tvChoiceLabel);

                String item = items[position];
                tv.setText(item);
                rb.setChecked(position == selectedPos);

                convertView.setOnClickListener(view -> {
                    dialog.dismiss();
                    if (onSelected != null) {
                        onSelected.onSelected(item);
                    }
                });

                return convertView;
            }
        };

        lvList.setAdapter(adapter);
        if (selectedPos >= 0) {
            lvList.setSelection(selectedPos);
        }

        // Only allocate that much space which is required:
        lvList.post(() -> {
            int totalHeight = 0;
            int maxAllowed = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.50);
            int widthSpec = View.MeasureSpec.makeMeasureSpec(
                    lvList.getWidth() > 0 ? lvList.getWidth() : (int) (context.getResources().getDisplayMetrics().widthPixels * 0.80),
                    View.MeasureSpec.AT_MOST);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            for (int i = 0; i < adapter.getCount(); i++) {
                View item = adapter.getView(i, null, lvList);
                item.measure(widthSpec, heightSpec);
                totalHeight += item.getMeasuredHeight();
                if (totalHeight >= maxAllowed) {
                    totalHeight = maxAllowed;
                    break;
                }
            }
            if (totalHeight > 0) {
                android.view.ViewGroup.LayoutParams lp = lvList.getLayoutParams();
                lp.height = Math.min(totalHeight + (int) (adapter.getCount() * 1.5f), maxAllowed);
                lvList.setLayoutParams(lp);
            }
        });

        btnCancel.setOnClickListener(view -> dialog.dismiss());
        com.samiy.financemanager.util.KeyboardHelper.setupOutsideTouchDismiss(dialog);

        dialog.show();
        return dialog;
    }

    public static Dialog showConfirmation(Context context, String title, String message, String positiveText, OnConfirmListener onConfirm) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View v = LayoutInflater.from(context).inflate(R.layout.dialog_finmngr_modal, null);
        dialog.setContentView(v);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = v.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = v.findViewById(R.id.tvDialogMessage);
        MaterialButton btnCancel = v.findViewById(R.id.btnDialogCancel);
        MaterialButton btnConfirm = v.findViewById(R.id.btnDialogConfirm);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnConfirm.setText(positiveText);

        btnCancel.setOnClickListener(view -> dialog.dismiss());
        btnConfirm.setOnClickListener(view -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.onConfirm();
        });

        dialog.show();
        return dialog;
    }

    public static Dialog showDestructiveConfirmation(Context context, String title, String message, String destructiveText, OnConfirmListener onConfirm) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View v = LayoutInflater.from(context).inflate(R.layout.dialog_finmngr_modal, null);
        dialog.setContentView(v);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = v.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = v.findViewById(R.id.tvDialogMessage);
        MaterialButton btnCancel = v.findViewById(R.id.btnDialogCancel);
        MaterialButton btnConfirm = v.findViewById(R.id.btnDialogConfirm);

        tvTitle.setText(title);
        tvMessage.setText(message);
        btnConfirm.setText(destructiveText);

        // Destructive button styling
        btnConfirm.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.red_bg));
        btnConfirm.setStrokeColor(ContextCompat.getColorStateList(context, R.color.red_rose));
        btnConfirm.setStrokeWidth(1);
        btnConfirm.setTextColor(ContextCompat.getColor(context, R.color.red_rose));

        btnCancel.setOnClickListener(view -> dialog.dismiss());
        btnConfirm.setOnClickListener(view -> {
            dialog.dismiss();
            if (onConfirm != null) onConfirm.onConfirm();
        });

        dialog.show();
        return dialog;
    }

    public static Dialog showAccountDialog(Context context, String name, String email, boolean authorized,
                                           Runnable onSwitchAccount, Runnable onSignOut) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View v = LayoutInflater.from(context).inflate(R.layout.dialog_finmngr_modal, null);
        dialog.setContentView(v);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.90);
            dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvTitle = v.findViewById(R.id.tvDialogTitle);
        TextView tvMessage = v.findViewById(R.id.tvDialogMessage);
        MaterialButton btnCancel = v.findViewById(R.id.btnDialogCancel);
        MaterialButton btnConfirm = v.findViewById(R.id.btnDialogConfirm);

        tvTitle.setText("Google Account");
        String status = authorized ? "Status: Authorized (Full Access)" : "Status: Not Authorized (samiyasti@gmail.com required)";
        tvMessage.setText("Signed in as:\n" + name + "\n" + email + "\n\n" + status);

        btnCancel.setText("Sign Out");
        btnCancel.setTextColor(ContextCompat.getColor(context, R.color.red_rose));
        btnConfirm.setText("Switch Account");

        btnCancel.setOnClickListener(view -> {
            dialog.dismiss();
            if (onSignOut != null) onSignOut.run();
        });

        btnConfirm.setOnClickListener(view -> {
            dialog.dismiss();
            if (onSwitchAccount != null) onSwitchAccount.run();
        });

        dialog.show();
        return dialog;
    }
}
