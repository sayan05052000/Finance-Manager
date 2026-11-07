package com.samiy.financemanager;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.samiy.financemanager.auth.GoogleAuthManager;
import com.samiy.financemanager.data.FinanceRepository;
import com.samiy.financemanager.data.model.CurrentCycle;
import com.samiy.financemanager.data.model.FinanceDatabase;
import com.samiy.financemanager.ui.dialog.AddMutualFundTransactionBottomSheet;
import com.samiy.financemanager.ui.dialog.AddTransactionBottomSheet;
import com.samiy.financemanager.ui.fragment.AccountsFragment;
import com.samiy.financemanager.ui.fragment.CycleFragment;
import com.samiy.financemanager.ui.fragment.HoldingsFragment;
import com.samiy.financemanager.ui.fragment.LedgerFragment;
import com.samiy.financemanager.ui.fragment.OverviewFragment;

import java.text.NumberFormat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements GoogleAuthManager.AuthListener {
    private TextView tvHeaderNetWorth, tvHeaderCycle, tvGoogleAuthText;
    private ImageButton btnRefresh;
    private View btnGoogleAuth;
    private BottomNavigationView bottomNav;
    private ExtendedFloatingActionButton fabAdd;
    private FrameLayout fragmentContainer;

    // Unauthorized Overlay Views
    private View layoutUnauthorized;
    private TextView tvUnauthorizedTitle, tvUnauthorizedReason;
    private MaterialButton btnUnauthorizedAction;

    private GoogleAuthManager authManager;
    private FinanceRepository repository;

    private Fragment currentFragment;
    private OverviewFragment overviewFragment;
    private CycleFragment cycleFragment;
    private HoldingsFragment holdingsFragment;
    private LedgerFragment ledgerFragment;
    private AccountsFragment accountsFragment;

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "IN"));

    private final Handler autoSyncHandler = new Handler(Looper.getMainLooper());
    private static final long AUTO_SYNC_INTERVAL_MS = 10000; // 10 seconds auto-resync
    private boolean isSyncing = false;

    private final Runnable autoSyncRunnable = new Runnable() {
        @Override
        public void run() {
            performAutoSync();
            autoSyncHandler.postDelayed(this, AUTO_SYNC_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repository = FinanceRepository.getInstance(this);
        authManager = GoogleAuthManager.getInstance(this);

        tvHeaderNetWorth = findViewById(R.id.tvHeaderNetWorth);
        tvHeaderCycle = findViewById(R.id.tvHeaderCycle);
        tvGoogleAuthText = findViewById(R.id.tvGoogleAuthText);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnGoogleAuth = findViewById(R.id.btnGoogleAuth);
        bottomNav = findViewById(R.id.bottomNavigation);
        fabAdd = findViewById(R.id.fabAdd);
        fragmentContainer = findViewById(R.id.fragmentContainer);

        layoutUnauthorized = findViewById(R.id.layoutUnauthorized);
        tvUnauthorizedTitle = findViewById(R.id.tvUnauthorizedTitle);
        tvUnauthorizedReason = findViewById(R.id.tvUnauthorizedReason);
        btnUnauthorizedAction = findViewById(R.id.btnUnauthorizedAction);

        overviewFragment = new OverviewFragment();
        cycleFragment = new CycleFragment();
        holdingsFragment = new HoldingsFragment();
        ledgerFragment = new LedgerFragment();
        accountsFragment = new AccountsFragment();

        setupHeader();
        setupNavigation();
        setupFab();
        setupAuth();

        // Apply initial authorization state
        applyAuthorizationState();

        // Check deep link if launched via OAuth redirect
        if (getIntent() != null) {
            authManager.handleDeepLink(getIntent());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        authManager.addListener(this);
        applyAuthorizationState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        authManager.removeListener(this);
        stopAutoSync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (authManager.isAuthorized()) {
            startAutoSync();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAutoSync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoSync();
    }

    private void performAutoSync() {
        if (!authManager.isAuthorized()) {
            stopAutoSync();
            return;
        }
        if (isSyncing || isFinishing() || isDestroyed()) return;
        isSyncing = true;
        repository.syncWithGoogleSheets(new FinanceRepository.DataCallback() {
            @Override
            public void onLoaded(FinanceDatabase db) {
                isSyncing = false;
                if (!isFinishing() && !isDestroyed()) {
                    if (btnRefresh != null) {
                        btnRefresh.animate().rotationBy(360f).setDuration(600).start();
                    }
                    updateHeaderInfo();
                    notifyActiveFragment();
                }
            }

            @Override
            public void onError(String error) {
                isSyncing = false;
                Log.w("MainActivity", "Auto-resync error: " + error);
            }
        });
    }

    private void startAutoSync() {
        autoSyncHandler.removeCallbacks(autoSyncRunnable);
        if (authManager.isAuthorized()) {
            autoSyncHandler.postDelayed(autoSyncRunnable, AUTO_SYNC_INTERVAL_MS);
        }
    }

    private void stopAutoSync() {
        autoSyncHandler.removeCallbacks(autoSyncRunnable);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null) {
            authManager.handleDeepLink(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (authManager.handleActivityResult(requestCode, resultCode, data)) {
            applyAuthorizationState();
        }
    }

    private void setupHeader() {
        updateHeaderInfo();

        btnRefresh.setOnClickListener(v -> {
            if (!authManager.isAuthorized()) {
                Toast.makeText(this, "You are not authorized", Toast.LENGTH_SHORT).show();
                return;
            }
            btnRefresh.animate().rotationBy(360f).setDuration(600).start();
            Toast.makeText(this, "Syncing with live Google Sheet...", Toast.LENGTH_SHORT).show();
            startAutoSync();
            repository.syncWithGoogleSheets(new FinanceRepository.DataCallback() {
                @Override
                public void onLoaded(FinanceDatabase db) {
                    updateHeaderInfo();
                    notifyActiveFragment();
                    Toast.makeText(MainActivity.this, "Google Sheet sync complete!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(MainActivity.this, "Sync error: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void updateHeaderInfo() {
        if (!authManager.isAuthorized()) {
            tvHeaderNetWorth.setText("Protected");
            tvHeaderCycle.setText("Access Restricted");
            return;
        }

        FinanceDatabase db = repository.getDatabase();
        if (db != null) {
            double netWorth = db.getEstimatedNetWorth();
            tvHeaderNetWorth.setText(formatCurrency(netWorth));

            CurrentCycle cycle = db.currentCycle != null ? db.currentCycle : new CurrentCycle();
            tvHeaderCycle.setText("⏳ " + cycle.cycleMonth + " • " + cycle.daysLeft + "d");

            // Update bottom nav badges
            try {
                BadgeDrawable cycleBadge = bottomNav.getOrCreateBadge(R.id.nav_cycle);
                cycleBadge.setNumber(5);
                cycleBadge.setBackgroundColor(getResources().getColor(R.color.green_emerald));

                BadgeDrawable holdingsBadge = bottomNav.getOrCreateBadge(R.id.nav_holdings);
                int assetsCount = db.holdings != null ? db.holdings.size() : 13;
                holdingsBadge.setNumber(assetsCount);
                holdingsBadge.setBackgroundColor(getResources().getColor(R.color.green_emerald));
            } catch (Exception ignored) {}
        }
    }

    private void setupNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            if (!authManager.isAuthorized()) {
                Toast.makeText(this, "You are not authorized", Toast.LENGTH_SHORT).show();
                return false;
            }
            int id = item.getItemId();
            if (id == R.id.nav_overview) {
                switchTab(overviewFragment);
                return true;
            } else if (id == R.id.nav_cycle) {
                switchTab(cycleFragment);
                return true;
            } else if (id == R.id.nav_holdings) {
                switchTab(holdingsFragment);
                return true;
            } else if (id == R.id.nav_ledger) {
                switchTab(ledgerFragment);
                return true;
            } else if (id == R.id.nav_accounts) {
                switchTab(accountsFragment);
                return true;
            }
            return false;
        });
    }

    private void switchTab(Fragment fragment) {
        currentFragment = fragment;
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.fragmentContainer, fragment);
        ft.commitAllowingStateLoss();
    }

    private void setupFab() {
        fabAdd.setOnClickListener(v -> {
            if (!authManager.isAuthorized()) {
                Toast.makeText(this, "You are not authorized", Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentFragment instanceof HoldingsFragment) {
                AddMutualFundTransactionBottomSheet dialog = new AddMutualFundTransactionBottomSheet();
                dialog.setListener(() -> {
                    updateHeaderInfo();
                    notifyActiveFragment();
                });
                dialog.show(getSupportFragmentManager(), "AddMfTxDialog");
            } else {
                AddTransactionBottomSheet dialog = new AddTransactionBottomSheet();
                dialog.setListener(() -> {
                    updateHeaderInfo();
                    notifyActiveFragment();
                });
                dialog.show(getSupportFragmentManager(), "AddTxDialog");
            }
        });

        fabAdd.setOnLongClickListener(v -> {
            if (!authManager.isAuthorized()) {
                Toast.makeText(this, "You are not authorized", Toast.LENGTH_SHORT).show();
                return true;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Record Transaction")
                    .setItems(new CharSequence[]{"General Transaction (Expense / Income / Transfer)", "Mutual Fund Transaction (Buy / SIP / Redeem)"}, (dialog, which) -> {
                        if (which == 0) {
                            AddTransactionBottomSheet txDialog = new AddTransactionBottomSheet();
                            txDialog.setListener(() -> {
                                updateHeaderInfo();
                                notifyActiveFragment();
                            });
                            txDialog.show(getSupportFragmentManager(), "AddTxDialog");
                        } else {
                            AddMutualFundTransactionBottomSheet mfDialog = new AddMutualFundTransactionBottomSheet();
                            mfDialog.setListener(() -> {
                                updateHeaderInfo();
                                notifyActiveFragment();
                            });
                            mfDialog.show(getSupportFragmentManager(), "AddMfTxDialog");
                        }
                    })
                    .show();
            return true;
        });
    }

    private void setupAuth() {
        btnUnauthorizedAction.setOnClickListener(v -> {
            if (authManager.isSignedIn()) {
                authManager.switchAccount(this);
            } else {
                authManager.signIn(this);
            }
        });

        btnGoogleAuth.setOnClickListener(v -> {
            if (authManager.isSignedIn()) {
                GoogleSignInAccount account = authManager.getCurrentAccount();
                String name = account != null && account.getDisplayName() != null ? account.getDisplayName() : "Google User";
                String email = account != null && account.getEmail() != null ? account.getEmail() : "";
                boolean authorized = authManager.isAuthorized();

                String status = authorized ? "\nStatus: Authorized (Full Access)" : "\nStatus: Not Authorized (samiyasti@gmail.com required)";

                new AlertDialog.Builder(this)
                        .setTitle("Google Account")
                        .setMessage("Signed in as:\n" + name + "\n" + email + "\n" + status)
                        .setPositiveButton("Switch Account", (dialog, which) -> {
                            authManager.switchAccount(this);
                        })
                        .setNeutralButton("Sign Out", (dialog, which) -> {
                            authManager.signOut(() -> {
                                Toast.makeText(this, "Signed out of Google", Toast.LENGTH_SHORT).show();
                            });
                        })
                        .setNegativeButton("Close", null)
                        .show();
            } else {
                Toast.makeText(this, "Connecting with Google OAuth 2.0...", Toast.LENGTH_SHORT).show();
                authManager.signIn(this);
            }
        });
    }

    @Override
    public void onAuthStateChanged(GoogleSignInAccount account) {
        if (account != null) {
            String name = account.getDisplayName();
            if (name != null && !name.trim().isEmpty()) {
                String firstWord = name.split(" ")[0];
                tvGoogleAuthText.setText(firstWord);
            } else if (account.getEmail() != null) {
                tvGoogleAuthText.setText(account.getEmail().split("@")[0]);
            } else {
                tvGoogleAuthText.setText("Active");
            }
        } else {
            tvGoogleAuthText.setText("Sign In");
        }
        applyAuthorizationState();
    }

    private void applyAuthorizationState() {
        boolean authorized = authManager.isAuthorized();
        GoogleSignInAccount account = authManager.getCurrentAccount();

        if (authorized) {
            layoutUnauthorized.setVisibility(View.GONE);
            fragmentContainer.setVisibility(View.VISIBLE);
            fabAdd.setVisibility(View.VISIBLE);
            btnRefresh.setEnabled(true);
            btnRefresh.setAlpha(1.0f);

            for (int i = 0; i < bottomNav.getMenu().size(); i++) {
                bottomNav.getMenu().getItem(i).setEnabled(true);
            }

            updateHeaderInfo();

            if (currentFragment == null) {
                switchTab(overviewFragment);
            } else {
                notifyActiveFragment();
            }

            startAutoSync();
        } else {
            stopAutoSync();
            layoutUnauthorized.setVisibility(View.VISIBLE);
            fragmentContainer.setVisibility(View.GONE);
            fabAdd.setVisibility(View.GONE);
            btnRefresh.setEnabled(false);
            btnRefresh.setAlpha(0.3f);

            for (int i = 0; i < bottomNav.getMenu().size(); i++) {
                bottomNav.getMenu().getItem(i).setEnabled(false);
            }

            tvHeaderNetWorth.setText("Protected");
            tvHeaderCycle.setText("Access Restricted");

            if (account != null && account.getEmail() != null) {
                String email = account.getEmail();
                tvUnauthorizedTitle.setText("You are not authorized");
                tvUnauthorizedReason.setText("Signed in as " + email + ".\n\nOnly the authorized Google account (" + GoogleAuthManager.AUTHORIZED_EMAIL + ") has permission to view or edit this portfolio and financial data.");
                btnUnauthorizedAction.setText("Switch to " + GoogleAuthManager.AUTHORIZED_EMAIL);
            } else {
                tvUnauthorizedTitle.setText("You are not authorized");
                tvUnauthorizedReason.setText("You are not signed in.\n\nPlease sign in with " + GoogleAuthManager.AUTHORIZED_EMAIL + " to view or edit your financial data.");
                btnUnauthorizedAction.setText("Sign in with " + GoogleAuthManager.AUTHORIZED_EMAIL);
            }
        }
    }

    private void notifyActiveFragment() {
        if (!authManager.isAuthorized()) return;

        if (currentFragment instanceof OverviewFragment) {
            ((OverviewFragment) currentFragment).populateUI();
        } else if (currentFragment instanceof HoldingsFragment) {
            ((HoldingsFragment) currentFragment).populateUI();
        } else if (currentFragment instanceof LedgerFragment) {
            ((LedgerFragment) currentFragment).populateUI();
        } else if (currentFragment instanceof CycleFragment) {
            ((CycleFragment) currentFragment).populateUI();
        } else if (currentFragment instanceof AccountsFragment) {
            ((AccountsFragment) currentFragment).populateUI();
        }
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount).replace("INR", "₹").replace(".00", "");
    }
}
