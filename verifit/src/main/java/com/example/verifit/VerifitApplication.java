package com.example.verifit;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.example.verifit.ui.MainActivity;

/**
 * Vague IHM-Dark (16/09/2026, retour Romain : "le bouton Light/Dark/Système dans les
 * Réglages, comme FitNotes").
 *
 * Seule raison d'etre de cette classe : appliquer le theme Light/Dark/Système choisi
 * par l'utilisateur (voir ThemeHelper) le plus tot possible dans le cycle de vie du
 * process - avant que la moindre Activity ne soit creee - pour que l'app s'ouvre
 * directement dans le bon theme, sans flash visuel ni recreation d'ecran. Declaree via
 * android:name=".VerifitApplication" dans AndroidManifest.xml.
 */
public class VerifitApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeHelper.applyStoredTheme(this);

        // Revue d'architecture du 28/09/2026 (point 1.2) : charger les donnees avant le
        // code de CHAQUE ecran, pas seulement de MainActivity. onActivityCreated() est
        // appele pendant le super.onCreate() de l'Activity, donc avant que l'ecran ne lise
        // ou n'ecrive dataStorage - y compris quand Android recree directement un ecran
        // apres avoir tue l'app en arriere-plan. Pas de chargement dans onCreate() de
        // l'Application elle-meme : elle demarre aussi pour le seul minuteur de repos
        // (RestTimerReceiver), qui n'a pas besoin de l'historique.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                MainActivity.dataStorage.ensureLoaded(activity);
                // Copie automatique quotidienne (point 1.4, voir BackupManager).
                BackupManager.dailySnapshotIfNeeded(activity, MainActivity.dataStorage);
            }
            @Override public void onActivityStarted(Activity activity) {}
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {}
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
    }
}
