package com.example.verifit;

import android.app.Application;

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
    }
}
