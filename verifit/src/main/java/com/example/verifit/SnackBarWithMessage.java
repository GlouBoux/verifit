package com.example.verifit;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.material.snackbar.Snackbar;

public class SnackBarWithMessage {
    private Context mContext;

    public SnackBarWithMessage(Context context) {
        mContext = context;
    }

    public void showSnackbar(String message) {
        showSnackbar(message, "Dismiss", null, Gravity.BOTTOM);
    }

    // Retour Romain 06/09/2026 : le bouton "Dismiss" existant fermait juste le message,
    // sans jamais annuler l'action qu'il annonce - trompeur pour une suppression ("j'ai
    // bien l'idée de garder le revert"). Ce deuxième point d'entrée fournit un vrai
    // geste d'annulation, exécuté au clic sur le bouton (devenu "Undo").
    public void showSnackbarWithUndo(String message, Runnable undoAction) {
        showSnackbar(message, "Undo", undoAction, Gravity.BOTTOM);
    }

    // Retour Romain 07/09/2026 : "la pop up dismiss/undo, tu pourrais la mettre en
    // haut de l'écran ? peut être au niveau du timer de séance ? [...] ça m'empêche de
    // visualiser ce que je viens d'ajouter avant que la pop up s'en aille." Variantes
    // "AtTop" ajoutées à côté des méthodes existantes (inchangées, toujours en bas -
    // utilisées ailleurs dans l'app : Login/Settings/MainActivity/ExerciseAdapter, pas
    // concernés par ce retour) plutôt que de changer le comportement par défaut de tout
    // le monde. Utilisées par AddExerciseActivity pour les messages liés à une série
    // (Set Added/Updated/Deleted+Undo/Restored...), sur l'écran qui porte justement la
    // barre de chrono de séance en haut.
    public void showSnackbarAtTop(String message) {
        showSnackbar(message, "Dismiss", null, Gravity.TOP);
    }

    public void showSnackbarWithUndoAtTop(String message, Runnable undoAction) {
        showSnackbar(message, "Undo", undoAction, Gravity.TOP);
    }

    private void showSnackbar(String message, String actionLabel, Runnable action, int gravity) {
        // Create the Snackbar
        Snackbar snackbar = Snackbar.make(((Activity) mContext).findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG);

        // Un Snackbar s'ancre par defaut en BAS de son parent (ici android.R.id.content,
        // un FrameLayout) - le repositionner en haut se fait en modifiant la gravite de
        // ses propres LayoutParams, pas via une option de Snackbar.Builder (qui n'existe
        // pas pour ca).
        if (gravity == Gravity.TOP)
        {
            View snackbarView = snackbar.getView();
            ViewGroup.LayoutParams layoutParams = snackbarView.getLayoutParams();
            if (layoutParams instanceof FrameLayout.LayoutParams)
            {
                ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.TOP;
                snackbarView.setLayoutParams(layoutParams);
            }
        }

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
