package com.samiy.financemanager.ui.dialog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.samiy.financemanager.R;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.MutualFundTransaction;
import com.samiy.financemanager.ui.adapter.MfTransactionsAdapter;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ViewMfTransactionsBottomSheet extends BaseBottomSheetDialogFragment {
    public static final String ARG_INITIAL_SCHEME = "initial_scheme";
    public static final String ARG_INITIAL_CATEGORY = "initial_category";
    public static final String ARG_INITIAL_SUBCATEGORY = "initial_subcategory";
    public static final String ARG_INITIAL_SCHEME_ID = "initial_scheme_id";
    public static final String ARG_INITIAL_SCHEME_CODE = "initial_scheme_code";

    private Spinner spFilterScheme, spFilterCategory, spFilterSubCategory, spFilterType;
    private TextView tvFilterCount, tvFilterTotalAmount, tvFilterTotalUnits, tvEmpty;
    private RecyclerView rvMfTransactions;
    private MfTransactionsAdapter adapter;

    private List<MutualFundTransaction> allTransactions = new ArrayList<>();
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    public static ViewMfTransactionsBottomSheet newInstance(@Nullable String initialScheme, @Nullable String initialCategory) {
        return newInstance(initialScheme, initialCategory, null, null, null);
    }

    public static ViewMfTransactionsBottomSheet newInstance(
            @Nullable String initialScheme,
            @Nullable String initialCategory,
            @Nullable String initialSubCategory,
            @Nullable String initialSchemeId,
            @Nullable String initialSchemeCode) {
        ViewMfTransactionsBottomSheet fragment = new ViewMfTransactionsBottomSheet();
        Bundle args = new Bundle();
        if (initialScheme != null) args.putString(ARG_INITIAL_SCHEME, initialScheme);
        if (initialCategory != null) args.putString(ARG_INITIAL_CATEGORY, initialCategory);
        if (initialSubCategory != null) args.putString(ARG_INITIAL_SUBCATEGORY, initialSubCategory);
        if (initialSchemeId != null) args.putString(ARG_INITIAL_SCHEME_ID, initialSchemeId);
        if (initialSchemeCode != null) args.putString(ARG_INITIAL_SCHEME_CODE, initialSchemeCode);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!com.samiy.financemanager.auth.GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            android.widget.Toast.makeText(requireContext(), "You are not authorized", android.widget.Toast.LENGTH_SHORT).show();
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_view_mf_transactions, container, false);

        spFilterScheme = v.findViewById(R.id.spMfFilterScheme);
        spFilterCategory = v.findViewById(R.id.spMfFilterCategory);
        spFilterSubCategory = v.findViewById(R.id.spMfFilterSubCategory);
        spFilterType = v.findViewById(R.id.spMfFilterType);

        tvFilterCount = v.findViewById(R.id.tvMfFilterCount);
        tvFilterTotalAmount = v.findViewById(R.id.tvMfFilterTotalAmount);
        tvFilterTotalUnits = v.findViewById(R.id.tvMfFilterTotalUnits);
        tvEmpty = v.findViewById(R.id.tvEmptyMfTransactions);

        rvMfTransactions = v.findViewById(R.id.rvMfTransactions);
        rvMfTransactions.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new MfTransactionsAdapter();
        rvMfTransactions.setAdapter(adapter);

        View btnClose = v.findViewById(R.id.btnViewMfClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(view -> dismiss());
        }

        loadDataAndSetupFilters();

        return v;
    }

    private void loadDataAndSetupFilters() {
        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        if (db != null && db.mutualFundTransactions != null) {
            allTransactions = db.mutualFundTransactions;
        } else {
            allTransactions = new ArrayList<>();
        }

        // Collect all unique schemes, categories, and subcategories from transactions and holdings
        Set<String> schemeSet = new HashSet<>();
        Set<String> categorySet = new HashSet<>();
        Set<String> subCategorySet = new HashSet<>();

        for (MutualFundTransaction tx : allTransactions) {
            if (tx.schemeName != null && !tx.schemeName.trim().isEmpty()) {
                schemeSet.add(tx.schemeName.trim());
            }
            if (tx.fundCategory != null && !tx.fundCategory.trim().isEmpty()) {
                categorySet.add(tx.fundCategory.trim());
            }
            if (tx.fundSubCategory != null && !tx.fundSubCategory.trim().isEmpty()) {
                subCategorySet.add(tx.fundSubCategory.trim());
            }
            if (tx.subCategory != null && !tx.subCategory.trim().isEmpty()) {
                subCategorySet.add(tx.subCategory.trim());
            }
        }

        if (db != null && db.holdings != null) {
            for (Holding h : db.holdings) {
                if (h.schemeName != null && !h.schemeName.trim().isEmpty()) {
                    schemeSet.add(h.schemeName.trim());
                }
                if (h.category != null && !h.category.trim().isEmpty()) {
                    categorySet.add(h.category.trim());
                }
                if (h.subCategory != null && !h.subCategory.trim().isEmpty()) {
                    subCategorySet.add(h.subCategory.trim());
                }
                if (h.savingsType != null && !h.savingsType.trim().isEmpty()) {
                    subCategorySet.add(h.savingsType.trim());
                }
            }
        }

        if (db != null && db.categories != null) {
            for (com.samiy.financemanager.data.model.CategoryItem c : db.categories) {
                if (c.subCategories != null) {
                    for (String sc : c.subCategories) {
                        if (sc != null && !sc.trim().isEmpty()) {
                            subCategorySet.add(sc.trim());
                        }
                    }
                }
            }
        }

        List<String> schemes = new ArrayList<>(schemeSet);
        Collections.sort(schemes);
        schemes.add(0, "All Schemes");

        List<String> categories = new ArrayList<>(categorySet);
        Collections.sort(categories);
        categories.add(0, "All Categories");

        List<String> subCategories = new ArrayList<>(subCategorySet);
        Collections.sort(subCategories);
        subCategories.add(0, "All Sub Categories");

        ArrayAdapter<String> schemeAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, schemes);
        schemeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spFilterScheme.setAdapter(schemeAdapter);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, categories);
        categoryAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spFilterCategory.setAdapter(categoryAdapter);

        ArrayAdapter<String> subCategoryAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, subCategories);
        subCategoryAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spFilterSubCategory.setAdapter(subCategoryAdapter);

        String[] types = new String[]{"All Types", "Buy / SIP", "Redeem"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, types);
        typeAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spFilterType.setAdapter(typeAdapter);

        // Pre-select if initial arguments passed
        String initScheme = getArguments() != null ? getArguments().getString(ARG_INITIAL_SCHEME) : null;
        if (initScheme != null) {
            for (int i = 0; i < schemes.size(); i++) {
                if (schemes.get(i).equalsIgnoreCase(initScheme.trim())) {
                    spFilterScheme.setSelection(i);
                    break;
                }
            }
        }

        String initCategory = getArguments() != null ? getArguments().getString(ARG_INITIAL_CATEGORY) : null;
        if (initCategory != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equalsIgnoreCase(initCategory.trim())) {
                    spFilterCategory.setSelection(i);
                    break;
                }
            }
        }

        String initSubCategory = getArguments() != null ? getArguments().getString(ARG_INITIAL_SUBCATEGORY) : null;
        if (initSubCategory != null) {
            for (int i = 0; i < subCategories.size(); i++) {
                if (subCategories.get(i).equalsIgnoreCase(initSubCategory.trim())) {
                    spFilterSubCategory.setSelection(i);
                    break;
                }
            }
        }

        AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spFilterScheme.setOnItemSelectedListener(filterListener);
        spFilterCategory.setOnItemSelectedListener(filterListener);
        spFilterSubCategory.setOnItemSelectedListener(filterListener);
        spFilterType.setOnItemSelectedListener(filterListener);

        applyFilters();
    }

    private void applyFilters() {
        String selectedScheme = spFilterScheme.getSelectedItem() != null ? spFilterScheme.getSelectedItem().toString() : "All Schemes";
        String selectedCategory = spFilterCategory.getSelectedItem() != null ? spFilterCategory.getSelectedItem().toString() : "All Categories";
        String selectedSubCategory = spFilterSubCategory.getSelectedItem() != null ? spFilterSubCategory.getSelectedItem().toString() : "All Sub Categories";
        String selectedType = spFilterType.getSelectedItem() != null ? spFilterType.getSelectedItem().toString() : "All Types";

        List<MutualFundTransaction> filtered = new ArrayList<>();
        double totalAmt = 0.0;
        double totalUnits = 0.0;

        for (MutualFundTransaction tx : allTransactions) {
            // 1. Scheme Filter
            if (!"All Schemes".equalsIgnoreCase(selectedScheme)) {
                String targetId = getArguments() != null ? getArguments().getString(ARG_INITIAL_SCHEME_ID) : null;
                String targetCode = getArguments() != null ? getArguments().getString(ARG_INITIAL_SCHEME_CODE) : null;
                boolean matchesScheme = (tx.schemeName != null && tx.schemeName.trim().equalsIgnoreCase(selectedScheme))
                        || (targetId != null && !targetId.isEmpty() && targetId.equalsIgnoreCase(tx.schemeId))
                        || (targetCode != null && !targetCode.isEmpty() && targetCode.equalsIgnoreCase(tx.schemeCode));
                if (!matchesScheme) {
                    continue;
                }
            }

            // 2. Category Filter
            if (!"All Categories".equalsIgnoreCase(selectedCategory)) {
                if (tx.fundCategory == null || !tx.fundCategory.trim().equalsIgnoreCase(selectedCategory)) {
                    continue;
                }
            }

            // 3. Sub-Category Filter
            if (!"All Sub Categories".equalsIgnoreCase(selectedSubCategory)) {
                boolean matchesSub = (tx.fundSubCategory != null && tx.fundSubCategory.trim().equalsIgnoreCase(selectedSubCategory))
                        || (tx.subCategory != null && tx.subCategory.trim().equalsIgnoreCase(selectedSubCategory));
                if (!matchesSub) {
                    continue;
                }
            }

            // 4. Type Filter (Buy vs Redeem)
            if ("Buy / SIP".equalsIgnoreCase(selectedType)) {
                if (tx.isRedeem()) {
                    continue;
                }
            } else if ("Redeem".equalsIgnoreCase(selectedType)) {
                if (!tx.isRedeem()) {
                    continue;
                }
            }

            filtered.add(tx);
            totalAmt += tx.getDisplayAmount();
            totalUnits += Math.abs(tx.units);
        }

        tvFilterCount.setText(String.valueOf(filtered.size()));
        tvFilterTotalAmount.setText(formatCurrency(totalAmt));
        tvFilterTotalUnits.setText(String.format(Locale.US, "%.3f", totalUnits));

        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvMfTransactions.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvMfTransactions.setVisibility(View.VISIBLE);
        }

        adapter.setItems(filtered);
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
