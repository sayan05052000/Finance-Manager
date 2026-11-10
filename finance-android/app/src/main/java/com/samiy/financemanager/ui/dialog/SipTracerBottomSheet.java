package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.SipItem;
import com.samiy.financemanager.ui.adapter.SipAdapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SipTracerBottomSheet extends BaseBottomSheetDialogFragment {
    private TextView tvSipSubtitle, tvEmptySips;
    private RecyclerView rvSipList;
    private ProgressBar pbSipLoading;
    private View layoutSipUpdatingBanner;
    private TextView tvSipUpdatingText;
    private SipAdapter adapter;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                (com.google.android.material.bottomsheet.BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            com.google.android.material.bottomsheet.BottomSheetDialog bsd =
                    (com.google.android.material.bottomsheet.BottomSheetDialog) d;
            android.widget.FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.widget.FrameLayout> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!com.samiy.financemanager.auth.GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            android.widget.Toast.makeText(requireContext(), "You are not authorized", android.widget.Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_sip_tracer, container, false);

        tvSipSubtitle = v.findViewById(R.id.tvSipSubtitle);
        tvEmptySips = v.findViewById(R.id.tvEmptySips);
        rvSipList = v.findViewById(R.id.rvSipList);
        pbSipLoading = v.findViewById(R.id.pbSipLoading);
        layoutSipUpdatingBanner = v.findViewById(R.id.layoutSipUpdatingBanner);
        tvSipUpdatingText = v.findViewById(R.id.tvSipUpdatingText);

        rvSipList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SipAdapter();
        rvSipList.setAdapter(adapter);

        View btnClose = v.findViewById(R.id.btnSipClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(view -> dismiss());
        }

        View btnSipRefresh = v.findViewById(R.id.btnSipRefresh);
        if (btnSipRefresh != null) {
            btnSipRefresh.setOnClickListener(view -> refreshSips());
        }

        View btnAddSip = v.findViewById(R.id.btnAddSip);
        if (btnAddSip != null) {
            btnAddSip.setOnClickListener(view -> {
                AddSipBottomSheet addDialog = AddSipBottomSheet.newInstance();
                addDialog.setListener(newSip -> loadData());
                addDialog.show(getParentFragmentManager(), "AddSipDialog");
            });
        }

        adapter.setListener(new SipAdapter.OnSipActionListener() {
            @Override
            public void onStatusChange(SipItem item, String targetMonth, String newStatus) {
                showUpdatingState("Updating " + item.fundName + " to " + newStatus + " in Google Sheets...");
                adapter.markUpdating(item.fundName, targetMonth);

                FinanceRepository.getInstance(requireContext()).updateSipStatus(
                        item.fundName, targetMonth, newStatus, new FinanceRepository.DataCallback() {
                            @Override
                            public void onLoaded(FinanceDatabase db) {
                                if (isAdded()) {
                                    hideUpdatingState();
                                    adapter.unmarkUpdating(item.fundName);
                                    loadData();
                                    com.samiy.financemanager.util.FinMngrToast.showSuccess(getContext(), item.fundName + " updated to " + newStatus);
                                }
                            }

                            @Override
                            public void onError(String error) {
                                if (isAdded()) {
                                    hideUpdatingState();
                                    adapter.unmarkUpdating(item.fundName);
                                    loadData();
                                    com.samiy.financemanager.util.FinMngrToast.show(getContext(), "Failed to update: " + error, Toast.LENGTH_SHORT);
                                }
                            }
                        });
            }

            @Override
            public void onAdvanceCycle(SipItem originalItem, SipItem advancedItem) {
                showUpdatingState("Advancing cycle for " + originalItem.fundName + " to " + advancedItem.currentMonth + "...");
                adapter.markUpdating(originalItem.fundName, "cycle");

                FinanceRepository.getInstance(requireContext()).updateSip(
                        advancedItem, new FinanceRepository.DataCallback() {
                            @Override
                            public void onLoaded(FinanceDatabase db) {
                                if (isAdded()) {
                                    hideUpdatingState();
                                    adapter.unmarkUpdating(originalItem.fundName);
                                    loadData();
                                    com.samiy.financemanager.util.FinMngrToast.showSuccess(getContext(), "Marked as Paid! Cycle advanced to " + advancedItem.currentMonth);
                                }
                            }

                            @Override
                            public void onError(String error) {
                                if (isAdded()) {
                                    hideUpdatingState();
                                    adapter.unmarkUpdating(originalItem.fundName);
                                    loadData();
                                    com.samiy.financemanager.util.FinMngrToast.show(getContext(), "Failed to advance cycle: " + error, Toast.LENGTH_LONG);
                                }
                            }
                        });
            }

            @Override
            public void onEditSip(SipItem item) {
                EditSipBottomSheet dialog = EditSipBottomSheet.newInstance(item);
                dialog.setListener(updatedSip -> {
                    loadData();
                });
                dialog.show(getParentFragmentManager(), "EditSipDialog");
            }

            @Override
            public void onDeleteSip(SipItem item) {
                showUpdatingState("Deleting " + item.fundName + " from Google Sheets...");
                adapter.markDeleting(item.fundName);

                FinanceRepository.getInstance(requireContext()).deleteSip(
                        item.fundName, new FinanceRepository.DataCallback() {
                            @Override
                            public void onLoaded(FinanceDatabase db) {
                                if (isAdded()) {
                                    hideUpdatingState();
                                    adapter.unmarkDeleting(item.fundName);
                                    loadData();
                                    com.samiy.financemanager.util.FinMngrToast.showSuccess(getContext(), "SIP deleted successfully");
                                }
                            }

                            @Override
                            public void onError(String error) {
                                if (isAdded()) {
                                    hideUpdatingState();
                                    adapter.unmarkDeleting(item.fundName);
                                    loadData();
                                    com.samiy.financemanager.util.FinMngrToast.show(getContext(), "Failed to delete: " + error, Toast.LENGTH_LONG);
                                }
                            }
                        });
            }
        });

        loadData();
        refreshSips();

        return v;
    }

    private final FinanceRepository.SyncListener syncListener = new FinanceRepository.SyncListener() {
        @Override
        public void onDatabaseSynced(FinanceDatabase database) {
            if (isAdded()) {
                loadData();
            }
        }
    };

    @Override
    public void onStart() {
        super.onStart();
        if (getContext() != null) {
            FinanceRepository.getInstance(requireContext()).addSyncListener(syncListener);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getContext() != null) {
            FinanceRepository.getInstance(requireContext()).removeSyncListener(syncListener);
        }
    }

    private void refreshSips() {
        showUpdatingState("Syncing SIPs with Google Sheets...");
        FinanceRepository.getInstance(requireContext()).syncSips(new FinanceRepository.DataCallback() {
            @Override
            public void onLoaded(FinanceDatabase db) {
                if (isAdded()) {
                    hideUpdatingState();
                    loadData();
                }
            }

            @Override
            public void onError(String error) {
                if (isAdded()) {
                    hideUpdatingState();
                    com.samiy.financemanager.util.FinMngrToast.show(getContext(), "Sync notice: " + error, Toast.LENGTH_SHORT);
                }
            }
        });
    }

    private void showUpdatingState(String message) {
        if (pbSipLoading != null) pbSipLoading.setVisibility(View.VISIBLE);
        if (layoutSipUpdatingBanner != null) layoutSipUpdatingBanner.setVisibility(View.VISIBLE);
        if (tvSipUpdatingText != null) tvSipUpdatingText.setText(message);
    }

    private void hideUpdatingState() {
        if (pbSipLoading != null) pbSipLoading.setVisibility(View.GONE);
        if (layoutSipUpdatingBanner != null) layoutSipUpdatingBanner.setVisibility(View.GONE);
    }

    private void loadData() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        List<SipItem> sips = (db != null && db.sips != null) ? db.sips : new ArrayList<>();

        double totalMonthlySip = 0.0;
        for (SipItem s : sips) {
            totalMonthlySip += s.currentSip;
        }

        if (tvSipSubtitle != null) {
            tvSipSubtitle.setText(sips.size() + " Active SIPs • Total Monthly: " + formatCurrency(totalMonthlySip));
        }

        if (sips.isEmpty()) {
            tvEmptySips.setVisibility(View.VISIBLE);
            rvSipList.setVisibility(View.GONE);
        } else {
            tvEmptySips.setVisibility(View.GONE);
            rvSipList.setVisibility(View.VISIBLE);
            adapter.setItems(sips);
        }
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
