package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.CreditCard;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.util.DateTimeHelper;

import java.text.NumberFormat;
import java.util.Locale;

public class EditCreditCardBottomSheet extends BottomSheetDialogFragment {
    private static final String ARG_CREDIT_CARD = "arg_credit_card";
    private CreditCard card;

    public interface OnCreditCardUpdatedListener {
        void onCreditCardUpdated();
    }

    private OnCreditCardUpdatedListener listener;

    public static EditCreditCardBottomSheet newInstance(CreditCard card) {
        EditCreditCardBottomSheet sheet = new EditCreditCardBottomSheet();
        Bundle args = new Bundle();
        args.putSerializable(ARG_CREDIT_CARD, card);
        sheet.setArguments(args);
        return sheet;
    }

    public void setOnCreditCardUpdatedListener(OnCreditCardUpdatedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            card = (CreditCard) getArguments().getSerializable(ARG_CREDIT_CARD);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.dialog_edit_credit_card, container, false);

        if (card == null) {
            dismissAllowingStateLoss();
            return v;
        }

        TextView tvCardName = v.findViewById(R.id.tvEditCardName);
        TextView tvUnbilled = v.findViewById(R.id.tvEditCardUnbilledSpends);
        EditText etBilled = v.findViewById(R.id.etEditBilledAmount);
        EditText etDueDate = v.findViewById(R.id.etEditDueDate);
        Button btnCancel = v.findViewById(R.id.btnEditCardCancel);
        Button btnSubmit = v.findViewById(R.id.btnEditCardSubmit);

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

        tvCardName.setText(card.cardName != null ? card.cardName : "Credit Card");
        tvUnbilled.setText("Unbilled Spends: " + currencyFormat.format(card.unbilledAmount).replace("INR", "₹").replace(".00", ""));

        if (card.billedAmount > 0) {
            etBilled.setText(String.format(Locale.US, "%.2f", card.billedAmount));
        } else {
            etBilled.setText("0.00");
        }

        if (card.dueDate != null && !card.dueDate.trim().isEmpty()) {
            etDueDate.setText(card.dueDate.trim());
        }

        DateTimeHelper.setupDatePicker(etDueDate);

        btnCancel.setOnClickListener(view -> dismiss());

        btnSubmit.setOnClickListener(view -> {
            if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                FinMngrToast.show(getContext(), "You are not authorized", Toast.LENGTH_SHORT);
                return;
            }

            String billedStr = etBilled.getText().toString().trim();
            String dueStr = etDueDate.getText().toString().trim();

            if (billedStr.isEmpty()) {
                FinMngrToast.show(getContext(), "Please enter billed amount", Toast.LENGTH_SHORT);
                return;
            }

            double billedAmount;
            try {
                billedAmount = Double.parseDouble(billedStr);
            } catch (Exception e) {
                FinMngrToast.show(getContext(), "Invalid billed amount", Toast.LENGTH_SHORT);
                return;
            }

            if (dueStr.isEmpty()) {
                FinMngrToast.show(getContext(), "Please select or enter due date", Toast.LENGTH_SHORT);
                return;
            }

            CreditCard updatedCard = new CreditCard();
            updatedCard.cardName = card.cardName;
            updatedCard.billedAmount = billedAmount;
            updatedCard.dueDate = dueStr;
            updatedCard.unbilledAmount = card.unbilledAmount;

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Updating Sheet...");

            FinanceRepository.getInstance(requireContext()).updateCreditCard(updatedCard, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase db) {
                    if (isAdded()) {
                        FinMngrToast.show(getContext(), card.cardName + " updated successfully!", Toast.LENGTH_SHORT);
                        if (listener != null) listener.onCreditCardUpdated();
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Save Changes");
                        FinMngrToast.show(getContext(), "Update failed: " + error, Toast.LENGTH_SHORT);
                    }
                }
            });
        });

        return v;
    }
}
