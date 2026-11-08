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
