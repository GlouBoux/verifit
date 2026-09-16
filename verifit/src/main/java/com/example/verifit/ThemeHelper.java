package com.example.verifit;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

/**
 * Vague IHM-Dark (16/09/2026, retour Romain : "le bouton Light/Dark/Système dans les
 * Réglages, comme FitNotes").
 *
 * Centralise la lecture/application du choix de thème stocké par la ListPreference
 * "theme" (res/xml/root_preferences.xml, valeurs possibles : "light" / "dark" /
 * "system" - voir res/values/arrays.xml). La preference est persistee automatiquement
 * par androidx.preference dans les SharedPreferences par defaut de l'app
 * (PreferenceManager.getDefaultSharedPreferences) - PAS dans le wrapper
 * SharedPreferences.java maison (celui-ci sert a autre chose : identifiants Webdav,
 * session verifit_rs...).
 *
 * applyStoredTheme() est appelee une seule fois au demarrage du process
 * (VerifitApplication.onCreate(), avant la creation de la moindre Activity) : c'est ce
 * qui fait que l'app s'ouvre directement dans le bon theme, sans flash ni recreation.
 * applyTheme() est appelee en plus par SettingsActivity des que l'utilisateur change la
 * valeur, pour un effet immediat sans avoir a rouvrir l'app.
 */
public class ThemeHelper {

    public static final String KEY_THEME = "theme";
    public static final String VALUE_LIGHT = "light";
    public static final String VALUE_DARK = "dark";
    public static final String VALUE_SYSTEM = "system";

    private ThemeHelper() {
        // Classe utilitaire, pas d'instanciation.
    }

    public static void applyStoredTheme(Context context) {
        String value = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_THEME, VALUE_SYSTEM);
        applyTheme(value);
    }

    public static void applyTheme(String value) {
        if (VALUE_LIGHT.equals(value)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (VALUE_DARK.equals(value)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            // "system" ou toute valeur inattendue : suit le reglage sombre du telephone,
            // meme comportement que le mode nuit automatique deja en place depuis le
            // passage d'AppTheme sur Theme.MaterialComponents.DayNight (voir styles.xml).
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
