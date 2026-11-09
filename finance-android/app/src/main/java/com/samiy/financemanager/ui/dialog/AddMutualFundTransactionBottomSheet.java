package com.samiy.financemanager.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.samiy.financemanager.R;
import com.samiy.financemanager.util.FinMngrToast;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.data.model.Holding;
import com.samiy.financemanager.data.model.MutualFundTransaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AddMutualFundTransactionBottomSheet extends BaseBottomSheetDialogFragment {
    public static final String ARG_LOCKED_SCHEME_NAME = "arg_locked_scheme_name";
    public static final String ARG_LOCKED_SUB_CATEGORY = "arg_locked_sub_category";
    public static final String ARG_LOCKED_SAVINGS_TYPE = "arg_locked_savings_type";
    public static final String ARG_LOCKED_CATEGORY = "arg_locked_category";
    public static final String ARG_LOCKED_PLATFORM = "arg_locked_platform";
    public static final String ARG_LOCKED_SCHEME_CODE = "arg_locked_scheme_code";
    public static final String ARG_LOCKED_SCHEME_ID = "arg_locked_scheme_id";
    public static final String ARG_LOCKED_NAV = "arg_locked_nav";

    public interface OnMfTransactionAddedListener {
        void onMfTransactionAdded();
    }

    private OnMfTransactionAddedListener listener;
    private int lastSelectedSchemePosition = -1;

    public static AddMutualFundTransactionBottomSheet newInstanceForHolding(Holding holding) {
        AddMutualFundTransactionBottomSheet sheet = new AddMutualFundTransactionBottomSheet();
        Bundle b = new Bundle();
        if (holding != null) {
            if (holding.schemeName != null) b.putString(ARG_LOCKED_SCHEME_NAME, holding.schemeName);
            if (holding.subCategory != null) b.putString(ARG_LOCKED_SUB_CATEGORY, holding.subCategory);
            if (holding.savingsType != null) b.putString(ARG_LOCKED_SAVINGS_TYPE, holding.savingsType);
            if (holding.category != null) b.putString(ARG_LOCKED_CATEGORY, holding.category);
            if (holding.source != null) b.putString(ARG_LOCKED_PLATFORM, holding.source);
            if (holding.schemeCode != null) b.putString(ARG_LOCKED_SCHEME_CODE, holding.schemeCode);
            if (holding.schemeId != null) b.putString(ARG_LOCKED_SCHEME_ID, holding.schemeId);
            b.putDouble(ARG_LOCKED_NAV, holding.nav);
        }
        sheet.setArguments(b);
        return sheet;
    }

    public static AddMutualFundTransactionBottomSheet newInstanceForFund(String schemeName, String subCategory) {
        AddMutualFundTransactionBottomSheet sheet = new AddMutualFundTransactionBottomSheet();
        Bundle b = new Bundle();
        if (schemeName != null) b.putString(ARG_LOCKED_SCHEME_NAME, schemeName);
        if (subCategory != null) {
            b.putString(ARG_LOCKED_SUB_CATEGORY, subCategory);
            b.putString(ARG_LOCKED_SAVINGS_TYPE, subCategory);
        }
        sheet.setArguments(b);
        return sheet;
    }

    public void setListener(OnMfTransactionAddedListener listener) {
        this.listener = listener;
    }

    // Helper item to preserve scheme code while showing scheme name in spinner
    public static class SchemeItem {
        public String schemeName = "";
        public String schemeCode = "";
        public String schemeId = "";
        public String fundCategory = "Equity";
        public String fundSubCategory = "Small Cap";
        public String platform = "Groww";
        public String savingsType = "Mutual Fund";
        public String defaultFolio = "";
        public List<String> folios = new ArrayList<>();
        public double lastNav = 0.0;

        @NonNull
        @Override
        public String toString() {
            return schemeName;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        if (!com.samiy.financemanager.auth.GoogleAuthManager.getInstance(requireContext()).isAuthorized()) {
            FinMngrToast.show(requireContext(), "You are not authorized", Toast.LENGTH_SHORT);
            dismissAllowingStateLoss();
            return null;
        }

        View v = inflater.inflate(R.layout.dialog_add_mf_transaction, container, false);

        TextView tvMfLockHint = v.findViewById(R.id.tvMfLockHint);
        Spinner spMfFund = v.findViewById(R.id.spMfFund);
        RadioGroup rgMfType = v.findViewById(R.id.rgMfType);
        RadioButton rbMfBuy = v.findViewById(R.id.rbMfBuy);
        RadioButton rbMfRedeem = v.findViewById(R.id.rbMfRedeem);

        AutoCompleteTextView etMfFolio = v.findViewById(R.id.etMfFolio);
        View btnMfFolioDropdown = v.findViewById(R.id.btnMfFolioDropdown);
        TextView tvMfFolioHint = v.findViewById(R.id.tvMfFolioHint);
        TextView tvMfAmountLabel = v.findViewById(R.id.tvMfAmountLabel);
        EditText etMfAmount = v.findViewById(R.id.etMfAmount);
        EditText etMfNav = v.findViewById(R.id.etMfNav);
        EditText etMfUnits = v.findViewById(R.id.etMfUnits);
        EditText etMfDate = v.findViewById(R.id.etMfDate);

        Spinner spMfCategory = v.findViewById(R.id.spMfCategory);
        Spinner spMfSubCategory = v.findViewById(R.id.spMfSubCategory);
        Spinner spMfSavingsType = v.findViewById(R.id.spMfSavingsType);
        Spinner spMfPlatform = v.findViewById(R.id.spMfPlatform);
        Button btnSubmit = v.findViewById(R.id.btnSubmitMfTransaction);

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        // Helper to close keyboard
        Runnable hideKeyboard = () -> {
            View currentFocus = getDialog() != null ? getDialog().getCurrentFocus() : null;
            if (currentFocus != null && imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                currentFocus.clearFocus();
            }
        };

        // Dismiss keyboard when touching outside or on selectors
        View.OnTouchListener touchDismissListener = (view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                hideKeyboard.run();
            }
            return false;
        };

        v.setOnTouchListener(touchDismissListener);
        spMfFund.setOnTouchListener(touchDismissListener);
        spMfCategory.setOnTouchListener(touchDismissListener);
        spMfSubCategory.setOnTouchListener(touchDismissListener);
        spMfSavingsType.setOnTouchListener(touchDismissListener);
        spMfPlatform.setOnTouchListener(touchDismissListener);
        rgMfType.setOnTouchListener(touchDismissListener);

        // Ensure proper keyboard opens when switching focus between input fields
        EditText[] allInputs = {etMfAmount, etMfNav, etMfUnits, etMfDate, etMfFolio};
        for (EditText et : allInputs) {
            et.setOnClickListener(view -> {
                if (imm != null) {
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                }
            });
            et.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus && imm != null) {
                    view.post(() -> imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT));
                }
            });
            com.samiy.financemanager.util.KeyboardHelper.setupDoneAction(et);
        }

        // Recursive keyboard dismissal on touching any non-input element
        setupTouchToDismissKeyboard(v, imm, allInputs);

        // Pre-fill today's date & wire calendar picker
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        etMfDate.setText(sdf.format(new Date()));
        com.samiy.financemanager.util.DateTimeHelper.setupPastDatePicker(etMfDate);

        // Check for locked arguments
        Bundle args = getArguments();
        boolean isLocked = false;
        String lockedSchemeName = "";
        String lockedSubCategory = "";
        String lockedSavingsType = "";
        String lockedCategory = "";
        String lockedPlatform = "";
        String lockedSchemeCode = "";
        String lockedSchemeId = "";
        double lockedNav = 0.0;

        if (args != null && args.containsKey(ARG_LOCKED_SCHEME_NAME)) {
            String nameArg = args.getString(ARG_LOCKED_SCHEME_NAME, "");
            if (nameArg != null && !nameArg.trim().isEmpty()) {
                isLocked = true;
                lockedSchemeName = nameArg.trim();
                lockedSubCategory = args.getString(ARG_LOCKED_SUB_CATEGORY, "");
                lockedSavingsType = args.getString(ARG_LOCKED_SAVINGS_TYPE, "");
                lockedCategory = args.getString(ARG_LOCKED_CATEGORY, "");
                lockedPlatform = args.getString(ARG_LOCKED_PLATFORM, "");
                lockedSchemeCode = args.getString(ARG_LOCKED_SCHEME_CODE, "");
                lockedSchemeId = args.getString(ARG_LOCKED_SCHEME_ID, "");
                lockedNav = args.getDouble(ARG_LOCKED_NAV, 0.0);
            }
        }

        FinanceDatabase db = FinanceRepository.getInstance(requireContext()).getDatabase();
        
        // Build unique scheme list mapping Scheme Name -> SchemeItem (with Scheme Code)
        Map<String, SchemeItem> schemeMap = new LinkedHashMap<>();

        if (db != null && db.holdings != null) {
            for (Holding h : db.holdings) {
                if (h.schemeName != null && !h.schemeName.trim().isEmpty()) {
                    SchemeItem item = new SchemeItem();
                    item.schemeName = h.schemeName.trim();
                    item.schemeCode = h.schemeCode != null ? h.schemeCode.trim() : "";
                    item.schemeId = h.schemeId != null ? h.schemeId.trim() : "";
                    item.fundCategory = h.category != null ? h.category : "Equity";
                    item.fundSubCategory = h.subCategory != null ? h.subCategory : "Small Cap";
                    item.platform = h.source != null ? h.source : "Groww";
                    item.savingsType = h.savingsType != null ? h.savingsType : "Mutual Fund";
                    item.lastNav = h.nav;
                    schemeMap.put(item.schemeName, item);
                }
            }
        }

        // Also check past mutual fund transactions for scheme codes and known folios
        if (db != null && db.mutualFundTransactions != null) {
            for (MutualFundTransaction tx : db.mutualFundTransactions) {
                if (tx.schemeName != null && !tx.schemeName.trim().isEmpty()) {
                    String name = tx.schemeName.trim();
                    SchemeItem item = schemeMap.get(name);
                    if (item == null) {
                        for (SchemeItem si : schemeMap.values()) {
                            if (si.schemeName.equalsIgnoreCase(name) ||
                                    (!si.schemeCode.isEmpty() && tx.schemeCode != null && si.schemeCode.equals(tx.schemeCode.trim())) ||
                                    (!si.schemeId.isEmpty() && tx.schemeId != null && si.schemeId.equalsIgnoreCase(tx.schemeId.trim()))) {
                                item = si;
                                break;
                            }
                        }
                    }

                    if (item == null) {
                        item = new SchemeItem();
                        item.schemeName = name;
                        item.schemeCode = tx.schemeCode != null ? tx.schemeCode.trim() : "";
                        item.schemeId = tx.schemeId != null ? tx.schemeId.trim() : "";
                        item.fundCategory = tx.fundCategory != null ? tx.fundCategory : "Equity";
                        item.fundSubCategory = tx.fundSubCategory != null ? tx.fundSubCategory : "Small Cap";
                        item.platform = tx.platform != null ? tx.platform : "Groww";
                        item.savingsType = tx.subCategory != null ? tx.subCategory : "Mutual Fund";
                        item.defaultFolio = tx.folioNumber != null ? tx.folioNumber.trim() : "";
                        item.lastNav = tx.nav;
                        schemeMap.put(name, item);
                    } else {
                        if ((item.schemeCode == null || item.schemeCode.isEmpty()) && tx.schemeCode != null && !tx.schemeCode.trim().isEmpty()) {
                            item.schemeCode = tx.schemeCode.trim();
                        }
                        if ((item.defaultFolio == null || item.defaultFolio.isEmpty()) && tx.folioNumber != null && !tx.folioNumber.trim().isEmpty()) {
                            item.defaultFolio = tx.folioNumber.trim();
                        }
                    }

                    // Collect folio strictly for this same scheme
                    if (tx.folioNumber != null && !tx.folioNumber.trim().isEmpty()) {
                        String fol = tx.folioNumber.trim();
                        if (!item.folios.contains(fol)) {
                            item.folios.add(fol);
                        }
                    }
                }
            }
        }

        List<SchemeItem> schemeList = new ArrayList<>(schemeMap.values());
        if (schemeList.isEmpty()) {
            SchemeItem default1 = new SchemeItem();
            default1.schemeName = "BANDHAN SMALL CAP FUND";
            default1.schemeCode = "147946";
            default1.schemeId = "INF194KB1AL4";
            schemeList.add(default1);

            SchemeItem default2 = new SchemeItem();
            default2.schemeName = "ICICI PRUDENTIAL LIQUID FUND";
            default2.schemeCode = "120197";
            default2.schemeId = "INF109K012F1";
            schemeList.add(default2);
        }

        // If locked fund is not in schemeList, insert it at position 0
        if (isLocked) {
            boolean found = false;
            for (SchemeItem si : schemeList) {
                if (si.schemeName.equalsIgnoreCase(lockedSchemeName)) {
                    found = true;
                    if (lockedNav > 0 && si.lastNav <= 0) si.lastNav = lockedNav;
                    if (!lockedSchemeCode.isEmpty() && si.schemeCode.isEmpty()) si.schemeCode = lockedSchemeCode;
                    if (!lockedSchemeId.isEmpty() && si.schemeId.isEmpty()) si.schemeId = lockedSchemeId;
                    break;
                }
            }
            if (!found) {
                SchemeItem item = new SchemeItem();
                item.schemeName = lockedSchemeName;
                item.schemeCode = lockedSchemeCode;
                item.schemeId = lockedSchemeId;
                item.fundCategory = !lockedCategory.isEmpty() ? lockedCategory : "Equity";
                item.fundSubCategory = !lockedSubCategory.isEmpty() ? lockedSubCategory : "Small Cap";
                item.platform = !lockedPlatform.isEmpty() ? lockedPlatform : "Groww";
                item.savingsType = !lockedSavingsType.isEmpty() ? lockedSavingsType : "Mutual Fund";
                item.lastNav = lockedNav;
                schemeList.add(0, item);
            }
        }

        // Show Scheme Names in the spinner with Dark UI styling
        ArrayAdapter<SchemeItem> fundAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, schemeList);
        fundAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spMfFund.setAdapter(fundAdapter);

        // Savings types (Sub-Category)
        List<String> savingsTypes = new ArrayList<>();
        savingsTypes.add("Mutual Fund");
        savingsTypes.add("Credit Mutual Fund");
        savingsTypes.add("Family Mutual Fund");
        savingsTypes.add("Emergency Mutual Fund");
        savingsTypes.add("Kuvera Surplus(Family)");
        savingsTypes.add("Brother Mutual Fund");

        String effectiveSavingsType = (lockedSavingsType != null && !lockedSavingsType.trim().isEmpty())
                ? lockedSavingsType.trim() : ((lockedSubCategory != null && !lockedSubCategory.trim().isEmpty()) ? lockedSubCategory.trim() : "Mutual Fund");
        if (isLocked && !savingsTypes.contains(effectiveSavingsType)) {
            savingsTypes.add(effectiveSavingsType);
        }

        ArrayAdapter<String> savingsAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, savingsTypes);
        savingsAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spMfSavingsType.setAdapter(savingsAdapter);

        // Platforms
        List<String> platforms = new ArrayList<>();
        platforms.add("Groww");
        platforms.add("Kuvera");
        platforms.add("Coin");
        if (isLocked && lockedPlatform != null && !lockedPlatform.trim().isEmpty() && !platforms.contains(lockedPlatform.trim())) {
            platforms.add(lockedPlatform.trim());
        }
        ArrayAdapter<String> platformAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, platforms);
        platformAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spMfPlatform.setAdapter(platformAdapter);

        // Fund Categories (Equity, Debt, Commodities)
        List<String> fundCategories = new ArrayList<>();
        fundCategories.add("Equity");
        fundCategories.add("Debt");
        fundCategories.add("Commodities");
        if (isLocked && lockedCategory != null && !lockedCategory.trim().isEmpty() && !fundCategories.contains(lockedCategory.trim())) {
            fundCategories.add(lockedCategory.trim());
        }
        ArrayAdapter<String> fundCategoryAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, fundCategories);
        fundCategoryAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spMfCategory.setAdapter(fundCategoryAdapter);

        // Fund Sub Categories (Silver, Gold, Liquid, Flexi Cap, Mid Cap, Small Cap, International, Short Duration, Index, Value)
        List<String> fundSubCategories = new ArrayList<>();
        fundSubCategories.add("Silver");
        fundSubCategories.add("Gold");
        fundSubCategories.add("Liquid");
        fundSubCategories.add("Flexi Cap");
        fundSubCategories.add("Mid Cap");
        fundSubCategories.add("Small Cap");
        fundSubCategories.add("International");
        fundSubCategories.add("Short Duration");
        fundSubCategories.add("Index");
        fundSubCategories.add("Value");
        if (isLocked && lockedSubCategory != null && !lockedSubCategory.trim().isEmpty() && !fundSubCategories.contains(lockedSubCategory.trim())) {
            fundSubCategories.add(lockedSubCategory.trim());
        }
        ArrayAdapter<String> fundSubCategoryAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_selected, fundSubCategories);
        fundSubCategoryAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        spMfSubCategory.setAdapter(fundSubCategoryAdapter);

        // Helper to retrieve folio suggestions: ONLY folios that have been used before for the SAME scheme
        java.util.function.Function<SchemeItem, List<String>> getFolioSuggestions = (scheme) -> {
            List<String> suggestions = new ArrayList<>();
            if (scheme != null) {
                if (scheme.defaultFolio != null && !scheme.defaultFolio.trim().isEmpty() && !suggestions.contains(scheme.defaultFolio.trim())) {
                    suggestions.add(scheme.defaultFolio.trim());
                }
                for (String f : scheme.folios) {
                    if (f != null && !f.trim().isEmpty() && !suggestions.contains(f.trim())) {
                        suggestions.add(f.trim());
                    }
                }
            }
            return suggestions;
        };

        // Folio Dropdown picker & AutoComplete
        etMfFolio.setOnClickListener(view -> etMfFolio.showDropDown());
        etMfFolio.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) etMfFolio.showDropDown();
        });

        btnMfFolioDropdown.setOnClickListener(btn -> {
            int selFundIdx = spMfFund.getSelectedItemPosition();
            SchemeItem currentScheme = (selFundIdx >= 0 && selFundIdx < schemeList.size()) ? schemeList.get(selFundIdx) : null;
            List<String> suggestions = getFolioSuggestions.apply(currentScheme);

            if (!suggestions.isEmpty()) {
                View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_folio, null);
                TextView tvScheme = dialogView.findViewById(R.id.tvFolioDialogScheme);
                if (tvScheme != null) {
                    tvScheme.setText(currentScheme != null ? currentScheme.schemeName : "Selected Scheme");
                }
                LinearLayout layoutItems = dialogView.findViewById(R.id.layoutFolioItems);

                AlertDialog dialog = new AlertDialog.Builder(requireContext())
                        .setView(dialogView)
                        .create();

                if (dialog.getWindow() != null) {
                    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
                }

                Button btnCancel = dialogView.findViewById(R.id.btnFolioDialogCancel);
                if (btnCancel != null) {
                    btnCancel.setOnClickListener(vCancel -> dialog.dismiss());
                }

                for (String folio : suggestions) {
                    View itemView = inflater.inflate(R.layout.item_folio_suggestion, layoutItems, false);
                    TextView tvVal = itemView.findViewById(R.id.tvFolioValue);
                    if (tvVal != null) tvVal.setText(folio);
                    itemView.setOnClickListener(vItem -> {
                        etMfFolio.setText(folio);
                        etMfFolio.setSelection(etMfFolio.getText().length());
                        dialog.dismiss();
                    });
                    layoutItems.addView(itemView);
                }

                dialog.show();
            } else {
                FinMngrToast.show(getContext(), "No previously used folios for this scheme. Please type your folio number.", Toast.LENGTH_SHORT);
            }
        });

        // Scheme Selector Listener with Form Reset on Scheme Change
        final boolean lockedMode = isLocked;
        spMfFund.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= schemeList.size()) return;
                SchemeItem selected = schemeList.get(position);

                boolean isSchemeChanged = (lastSelectedSchemePosition != -1 && lastSelectedSchemePosition != position);
                lastSelectedSchemePosition = position;

                // Update autocomplete suggestions for folio (strictly for this same scheme)
                List<String> suggestions = getFolioSuggestions.apply(selected);
                ArrayAdapter<String> folioAdapter = new ArrayAdapter<>(requireContext(), R.layout.item_spinner_dropdown, suggestions);
                etMfFolio.setAdapter(folioAdapter);

                if (tvMfFolioHint != null) {
                    if (!suggestions.isEmpty()) {
                        tvMfFolioHint.setText(suggestions.size() + " saved folio" + (suggestions.size() > 1 ? "s" : "") + " for this scheme");
                    } else {
                        tvMfFolioHint.setText("No previous folio for this scheme (Type new)");
                    }
                }

                if (lockedMode) {
                    if (etMfFolio.getText().toString().trim().isEmpty()) {
                        if (!selected.folios.isEmpty()) {
                            etMfFolio.setText(selected.folios.get(0));
                        } else if (selected.defaultFolio != null && !selected.defaultFolio.isEmpty()) {
                            etMfFolio.setText(selected.defaultFolio);
                        }
                    }
                    if (etMfNav.getText().toString().trim().isEmpty() && selected.lastNav > 0) {
                        etMfNav.setText(String.format(Locale.US, "%.3f", selected.lastNav));
                    }
                    return;
                }

                if (isSchemeChanged) {
                    // USER RULE: "if scheme changes form also resets"
                    etMfAmount.setText("");
                    etMfUnits.setText("");
                    rbMfBuy.setChecked(true);
                    tvMfAmountLabel.setText("Invested Amount (₹)");
                    etMfAmount.setHint("e.g. 5000.00");
                    etMfUnits.setHint("e.g. 89.605");

                    // Reset NAV
                    if (selected.lastNav > 0) {
                        etMfNav.setText(String.format(Locale.US, "%.3f", selected.lastNav));
                    } else {
                        etMfNav.setText("");
                    }

                    // Reset Folio to selected scheme's known folio or empty
                    if (!selected.folios.isEmpty()) {
                        etMfFolio.setText(selected.folios.get(0));
                    } else if (selected.defaultFolio != null && !selected.defaultFolio.isEmpty()) {
                        etMfFolio.setText(selected.defaultFolio);
                    } else {
                        etMfFolio.setText("");
                    }

                    // Reset Savings Type to scheme default
                    if (selected.savingsType != null) {
                        for (int i = 0; i < savingsTypes.size(); i++) {
                            if (savingsTypes.get(i).equalsIgnoreCase(selected.savingsType)) {
                                spMfSavingsType.setSelection(i);
                                break;
                            }
                        }
                    }

                    // Reset Platform to scheme default
                    if (selected.platform != null) {
                        for (int i = 0; i < platforms.size(); i++) {
                            if (platforms.get(i).equalsIgnoreCase(selected.platform)) {
                                spMfPlatform.setSelection(i);
                                break;
                            }
                        }
                    }

                    // Reset Fund Category to scheme default
                    if (selected.fundCategory != null) {
                        for (int i = 0; i < fundCategories.size(); i++) {
                            if (fundCategories.get(i).equalsIgnoreCase(selected.fundCategory)) {
                                spMfCategory.setSelection(i);
                                break;
                            }
                        }
                    }

                    // Reset Fund Sub Category to scheme default
                    if (selected.fundSubCategory != null) {
                        for (int i = 0; i < fundSubCategories.size(); i++) {
                            if (fundSubCategories.get(i).equalsIgnoreCase(selected.fundSubCategory)) {
                                spMfSubCategory.setSelection(i);
                                break;
                            }
                        }
                    }

                    FinMngrToast.show(getContext(), "Form reset for " + selected.schemeName, Toast.LENGTH_SHORT);
                } else {
                    // Initial load defaults
                    if (selected.lastNav > 0 && etMfNav.getText().toString().trim().isEmpty()) {
                        etMfNav.setText(String.format(Locale.US, "%.3f", selected.lastNav));
                    }
                    if (etMfFolio.getText().toString().trim().isEmpty()) {
                        if (!selected.folios.isEmpty()) {
                            etMfFolio.setText(selected.folios.get(0));
                        } else if (selected.defaultFolio != null && !selected.defaultFolio.isEmpty()) {
                            etMfFolio.setText(selected.defaultFolio);
                        }
                    }
                    if (selected.savingsType != null) {
                        for (int i = 0; i < savingsTypes.size(); i++) {
                            if (savingsTypes.get(i).equalsIgnoreCase(selected.savingsType)) {
                                spMfSavingsType.setSelection(i);
                                break;
                            }
                        }
                    }
                    if (selected.platform != null) {
                        for (int i = 0; i < platforms.size(); i++) {
                            if (platforms.get(i).equalsIgnoreCase(selected.platform)) {
                                spMfPlatform.setSelection(i);
                                break;
                            }
                        }
                    }
                    if (selected.fundCategory != null) {
                        for (int i = 0; i < fundCategories.size(); i++) {
                            if (fundCategories.get(i).equalsIgnoreCase(selected.fundCategory)) {
                                spMfCategory.setSelection(i);
                                break;
                            }
                        }
                    }
                    if (selected.fundSubCategory != null) {
                        for (int i = 0; i < fundSubCategories.size(); i++) {
                            if (fundSubCategories.get(i).equalsIgnoreCase(selected.fundSubCategory)) {
                                spMfSubCategory.setSelection(i);
                                break;
                            }
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Apply Locked Auto-Selection and strictly disable modifications
        if (isLocked) {
            // 1. Select and lock Fund
            for (int i = 0; i < schemeList.size(); i++) {
                if (schemeList.get(i).schemeName.equalsIgnoreCase(lockedSchemeName)) {
                    spMfFund.setSelection(i);
                    lastSelectedSchemePosition = i;
                    break;
                }
            }
            spMfFund.setEnabled(false);
            spMfFund.setOnTouchListener((view, event) -> true);

            // 2. Select and lock Savings Type / Sub-Category
            for (int i = 0; i < savingsTypes.size(); i++) {
                if (savingsTypes.get(i).equalsIgnoreCase(effectiveSavingsType)) {
                    spMfSavingsType.setSelection(i);
                    break;
                }
            }
            spMfSavingsType.setEnabled(false);
            spMfSavingsType.setOnTouchListener((view, event) -> true);

            // 3. Select and lock Fund Sub-Category
            if (lockedSubCategory != null && !lockedSubCategory.trim().isEmpty()) {
                for (int i = 0; i < fundSubCategories.size(); i++) {
                    if (fundSubCategories.get(i).equalsIgnoreCase(lockedSubCategory.trim())) {
                        spMfSubCategory.setSelection(i);
                        break;
                    }
                }
                spMfSubCategory.setEnabled(false);
                spMfSubCategory.setOnTouchListener((view, event) -> true);
            }

            // 4. Select and lock Fund Category
            if (lockedCategory != null && !lockedCategory.trim().isEmpty()) {
                for (int i = 0; i < fundCategories.size(); i++) {
                    if (fundCategories.get(i).equalsIgnoreCase(lockedCategory.trim())) {
                        spMfCategory.setSelection(i);
                        break;
                    }
                }
                spMfCategory.setEnabled(false);
                spMfCategory.setOnTouchListener((view, event) -> true);
            }

            // 5. Select and lock Platform
            if (lockedPlatform != null && !lockedPlatform.trim().isEmpty()) {
                for (int i = 0; i < platforms.size(); i++) {
                    if (platforms.get(i).equalsIgnoreCase(lockedPlatform.trim())) {
                        spMfPlatform.setSelection(i);
                        break;
                    }
                }
                spMfPlatform.setEnabled(false);
                spMfPlatform.setOnTouchListener((view, event) -> true);
            }

            // 6. Pre-fill NAV if available
            if (lockedNav > 0) {
                etMfNav.setText(String.format(Locale.US, "%.3f", lockedNav));
            }

            // 7. Show user lock confirmation banner
            if (tvMfLockHint != null) {
                tvMfLockHint.setVisibility(View.VISIBLE);
                tvMfLockHint.setText("🔒 Auto-selected Fund: " + lockedSchemeName + " • Sub-Category: " + effectiveSavingsType + " (Cannot be changed)");
            }
        }

        // Toggle Buy vs Redeem
        rgMfType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbMfRedeem) {
                tvMfAmountLabel.setText("Amount (₹) [Not required for Redeem]");
                etMfAmount.setHint("Not required for Redeem");
                etMfUnits.setHint("Units (Auto negative)");
            } else {
                tvMfAmountLabel.setText("Invested Amount (₹)");
                etMfAmount.setHint("e.g. 5000.00");
                etMfUnits.setHint("e.g. 89.605");
            }
        });

        final String finalLockedSchemeName = lockedSchemeName;
        final String finalLockedSchemeCode = lockedSchemeCode;
        final String finalLockedSchemeId = lockedSchemeId;

        btnSubmit.setOnClickListener(view -> {
            boolean isBuy = rbMfBuy.isChecked();

            String amtStr = etMfAmount.getText().toString().trim();
            String navStr = etMfNav.getText().toString().trim();
            String unitsStr = etMfUnits.getText().toString().trim();
            String folioStr = etMfFolio.getText().toString().trim();

            double amount = 0.0;
            if (isBuy) {
                if (amtStr.isEmpty()) {
                    FinMngrToast.show(getContext(), "Please enter an amount for Buy transaction", Toast.LENGTH_SHORT);
                    return;
                }
                try {
                    amount = Double.parseDouble(amtStr);
                } catch (Exception e) {
                    FinMngrToast.show(getContext(), "Invalid amount", Toast.LENGTH_SHORT);
                    return;
                }
            } else {
                if (!amtStr.isEmpty()) {
                    try {
                        amount = Double.parseDouble(amtStr);
                    } catch (Exception ignored) {}
                }
            }

            if (navStr.isEmpty()) {
                FinMngrToast.show(getContext(), "Please enter NAV", Toast.LENGTH_SHORT);
                return;
            }
            double nav;
            try {
                nav = Double.parseDouble(navStr);
            } catch (Exception e) {
                FinMngrToast.show(getContext(), "Invalid NAV", Toast.LENGTH_SHORT);
                return;
            }

            if (unitsStr.isEmpty()) {
                FinMngrToast.show(getContext(), "Please enter Units", Toast.LENGTH_SHORT);
                return;
            }
            double units;
            try {
                units = Double.parseDouble(unitsStr);
            } catch (Exception e) {
                FinMngrToast.show(getContext(), "Invalid Units", Toast.LENGTH_SHORT);
                return;
            }

            // User rule: For redeem, units will ALWAYS be considered as negative
            if (!isBuy) {
                units = -Math.abs(units);
                if (amount > 0) {
                    amount = -amount;
                }
            } else {
                units = Math.abs(units);
                amount = Math.abs(amount);
            }

            int selFundIdx = spMfFund.getSelectedItemPosition();
            SchemeItem selectedItem = (selFundIdx >= 0 && selFundIdx < schemeList.size()) ? schemeList.get(selFundIdx) : null;

            MutualFundTransaction mfTx = new MutualFundTransaction();
            mfTx.date = etMfDate.getText().toString().trim();
            mfTx.amount = amount;
            mfTx.actualInvested = amount; // User rule: amount goes to Actual Invested
            mfTx.nav = nav;               // User rule: nav goes to Buy NAV/Reedem NAV
            mfTx.currentNav = nav;
            mfTx.units = units;           // User rule: units goes to units (negative for redeem)
            mfTx.investedRedeemedAmount = ""; // User rule: Column 9 kept empty
            mfTx.folioNumber = folioStr;  // User rule: Folio number (picked from suggestions or typed by user)
            mfTx.platform = spMfPlatform.getSelectedItem() != null ? spMfPlatform.getSelectedItem().toString() : "Groww";
            mfTx.subCategory = spMfSavingsType.getSelectedItem() != null ? spMfSavingsType.getSelectedItem().toString() : "Mutual Fund";

            if (selectedItem != null) {
                mfTx.schemeName = selectedItem.schemeName;
                mfTx.schemeId = !selectedItem.schemeId.isEmpty() ? selectedItem.schemeId : finalLockedSchemeId;
                mfTx.schemeCode = !selectedItem.schemeCode.isEmpty() ? selectedItem.schemeCode : finalLockedSchemeCode;
                mfTx.fundCategory = spMfCategory.getSelectedItem() != null ? spMfCategory.getSelectedItem().toString() : selectedItem.fundCategory;
                mfTx.fundSubCategory = spMfSubCategory.getSelectedItem() != null ? spMfSubCategory.getSelectedItem().toString() : selectedItem.fundSubCategory;
            } else {
                mfTx.schemeName = !finalLockedSchemeName.isEmpty() ? finalLockedSchemeName : (spMfFund.getSelectedItem() != null ? spMfFund.getSelectedItem().toString() : "Mutual Fund");
                mfTx.schemeCode = finalLockedSchemeCode;
                mfTx.schemeId = finalLockedSchemeId;
                mfTx.fundCategory = spMfCategory.getSelectedItem() != null ? spMfCategory.getSelectedItem().toString() : "Equity";
                mfTx.fundSubCategory = spMfSubCategory.getSelectedItem() != null ? spMfSubCategory.getSelectedItem().toString() : "Small Cap";
            }

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Syncing MF Tx to Sheet...");

            FinanceRepository.getInstance(requireContext()).addMutualFundTransaction(mfTx, new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase updatedDb) {
                    if (isAdded()) {
                        FinMngrToast.show(getContext(), "Mutual Fund transaction recorded in Google Sheet!", Toast.LENGTH_LONG);
                        if (listener != null) listener.onMfTransactionAdded();
                        dismiss();
                    }
                }

                @Override
                public void onError(String error) {
                    if (isAdded()) {
                        FinMngrToast.show(getContext(), error, Toast.LENGTH_LONG);
                        if (listener != null) listener.onMfTransactionAdded();
                        dismiss();
                    }
                }
            });
        });

        return v;
    }

    private void setupTouchToDismissKeyboard(View view, InputMethodManager imm, EditText... inputs) {
        if (view == null) return;
        boolean isInput = false;
        for (EditText in : inputs) {
            if (view == in) {
                isInput = true;
                break;
            }
        }
        if (!isInput && !(view instanceof EditText)) {
            view.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    View currentFocus = getDialog() != null ? getDialog().getCurrentFocus() : null;
                    if (currentFocus != null && imm != null) {
                        imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                        currentFocus.clearFocus();
                    }
                }
                return false;
            });
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setupTouchToDismissKeyboard(vg.getChildAt(i), imm, inputs);
            }
        }
    }
}