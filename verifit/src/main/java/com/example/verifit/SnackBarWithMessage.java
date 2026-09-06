package com.example.verifit;

import android.app.Activity;
import android.content.Context;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

public class SnackBarWithMessage {
    private Context mContext;

    public SnackBarWithMessage(Context context) {
        mContext = context;
    }

    public void showSnackbar(String message) {
        showSnackbar(message, "Dismiss", null);
    }

    // Retour Romain 06/09/2026 : le bouton "Dismiss" existant fermait juste le message,
    // sans jamais annuler l'action qu'il annonce - trompeur pour une suppression ("j'ai
    // bien l'idée de garder le revert"). Ce deuxième point d'entrée fournit un vrai
    // geste d'annulation, exécuté au clic sur le bouton (devenu "Undo").
    public void showSnackbarWithUndo(String message, Runnable undoAction) {
        showSnackbar(message, "Undo", undoAction);
    }

    private void showSnackbar(String message, String actionLabel, Runnable action) {
        // Create the Snackbar
        Snackbar snackbar = Snackbar.make(((Activity) mContext).findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG);


        // Set an action for the Snackbar
        snackbar.setAction(actionLabel, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (action != null) {
                    action.run();
                }
                snackbar.dismiss();
            }
        });


        // Show the Snackbar
        snackbar.show();
    }
}

