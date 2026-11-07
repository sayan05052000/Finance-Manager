package com.samiy.financemanager.auth;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.List;

public class GoogleAuthManager {
    public static final int RC_SIGN_IN = 9001;
    public static final String AUTHORIZED_EMAIL = "samiyasti@gmail.com";

    private static GoogleAuthManager instance;
    private final Context context;
    private GoogleSignInClient googleSignInClient;
    private GoogleSignInAccount currentAccount;
    private final List<AuthListener> listeners = new ArrayList<>();

    public interface AuthListener {
        void onAuthStateChanged(GoogleSignInAccount account);
    }

    public static synchronized GoogleAuthManager getInstance(Context context) {
        if (instance == null) {
            instance = new GoogleAuthManager(context.getApplicationContext());
        }
        return instance;
    }

    private GoogleAuthManager(Context context) {
        this.context = context;
        initClient();
    }

    private void initClient() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(context, gso);
        currentAccount = GoogleSignIn.getLastSignedInAccount(context);
    }

    public void addListener(AuthListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        listener.onAuthStateChanged(currentAccount);
    }

    public void removeListener(AuthListener listener) {
        listeners.remove(listener);
    }

    public GoogleSignInAccount getCurrentAccount() {
        return currentAccount;
    }

    public boolean isSignedIn() {
        return currentAccount != null;
    }

    public boolean isAuthorized() {
        return currentAccount != null && isAuthorizedEmail(currentAccount.getEmail());
    }

    public static boolean isAuthorizedEmail(String email) {
        return email != null && AUTHORIZED_EMAIL.equalsIgnoreCase(email.trim());
    }

    public void signIn(Activity activity) {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        activity.startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    public void switchAccount(Activity activity) {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            currentAccount = null;
            notifyListeners();
            Intent signInIntent = googleSignInClient.getSignInIntent();
            activity.startActivityForResult(signInIntent, RC_SIGN_IN);
        });
    }

    public void signOut(Runnable onComplete) {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            currentAccount = null;
            notifyListeners();
            if (onComplete != null) onComplete.run();
        });
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                currentAccount = task.getResult(ApiException.class);
                notifyListeners();
                return true;
            } catch (ApiException e) {
                Log.w("GoogleAuthManager", "signInResult:failed code=" + e.getStatusCode());
                currentAccount = GoogleSignIn.getLastSignedInAccount(context);
                notifyListeners();
            }
            return true;
        }
        return false;
    }

    public boolean handleDeepLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            if ("com.samiy.financemanager".equals(data.getScheme()) && "oauth2redirect".equals(data.getHost())) {
                notifyListeners();
                return true;
            }
        }
        return false;
    }

    private void notifyListeners() {
        for (AuthListener l : new ArrayList<>(listeners)) {
            l.onAuthStateChanged(currentAccount);
        }
    }
}
