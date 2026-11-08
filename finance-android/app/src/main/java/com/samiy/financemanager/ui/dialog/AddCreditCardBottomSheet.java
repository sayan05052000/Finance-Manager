package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

import java.util.List;

public class AddCreditCardBottomSheet extends BottomSheetDialogFragment {
    public interface OnCreditCardAddedListener {
        void onCreditCardAdded(CreditCard newCard);
    }

    private OnCreditCardAddedListener listener;

    public static AddCreditCardBottomSheet newInstance() {
        return new AddCreditCardBottomSheet();
    }

    public void setOnCreditCardAddedListener(OnCreditCardAddedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.show(getContext(), "You are not authorized", Toast.LENGTH_SHORT);
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_add_credit_card, container, false);

        EditText etCardName = v.findViewById(R.id.etAddCardName);
        EditText etDueDate = v.findViewById(R.id.etAddCardDueDate);
        EditText etBilledAmount = v.findViewById(R.id.etAddCardBilledAmount);
        Button btnCancel = v.findViewById(R.id.btnAddCardCancel);
        Button btnSubmit = v.findViewById(R.id.btnAddCardSubmit);

        DateTimeHelper.setupDatePicker(etDueDate);

        btnCancel.setOnClickListener(view -> dismiss());

        btnSubmit.setOnClickListener(view -> {
            if (!GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
                FinMngrToast.show(getContext(), "You are not authorized", Toast.LENGTH_SHORT);
                return;
            }

            String cardName = etCardName.getText().toString().trim();
            if (cardName.isEmpty()) {
                etCardName.setError("Card name is required");
                etCardName.requestFocus();
                return;
            }

            // Uniqueness check: name must be unique among existing cards
            FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
            if (db != null && db.creditCards != null) {
                for (CreditCard existing : db.creditCards) {
                    if (existing != null && existing.cardName != null &&
                            existing.cardName.trim().equalsIgnoreCase(cardName)) {
                        etCardName.setError("A card with this name already exists");
                        etCardName.requestFocus();
                        FinMngrToast.show(getContext(), "Card '" + cardName + "' already exists! Card names must be unique.", Toast.LENGTH_LONG);
                        return;
                    }
                }
            }

            String dueStr = etDueDate.getText().toString().trim();
            String billedStr = etBilledAmount.getText().toString().trim();
            double billedAmount = 0.0;
            if (!billedStr.isEmpty()) {
                try {
                    billedAmount = Double.parseDouble(billedStr);
                } catch (Exception e) {
                    etBilledAmount.setError("Invalid billed amount");
                    etBilledAmount.requestFocus();
                    return;
                }
            }

            CreditCard newCard = new CreditCard();
            newCard.cardName = cardName;
            newCard.dueDate = dueStr;
            newCard.billedAmount = billedAmount;
            newCard.unbilledAmount = 0.0; // No unbilled amount taken, defaults to 0
            newCard.status = billedAmount > 0 ? "Unpaid" : "Paid";

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Adding Card...");

            FinanceRepository.getInstance(requireContext()).addCreditCard(newCard, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        FinMngrToast.show(getContext(), "Credit Card " + newCard.cardName + " added!", Toast.LENGTH_SHORT);
                        if (listener != null) listener.onCreditCardAdded(newCard);
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("+ Add Card");
                        FinMngrToast.show(getContext(), "Failed to add card: " + error, Toast.LENGTH_SHORT);
                    }
                }
            });
        });

        return v;
    }
}
