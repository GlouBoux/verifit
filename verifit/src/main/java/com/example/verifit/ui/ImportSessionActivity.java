package com.example.verifit.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.verifit.AppNames;
import com.example.verifit.DataStorage;
import com.example.verifit.R;
import com.example.verifit.SessionImporter;
import com.example.verifit.SessionPreview;
import com.example.verifit.TextFiles;
import com.example.verifit.model.Exercise;
import com.example.verifit.model.ImportedSession;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

/**
 * "Ouvrir avec FitEngine" (lot D, etapes D7 et D8, 02/10/2026) : recoit une seance JSON
 * envoyee par une autre appli (Drive, mail, Discord, fichier telecharge...), en montre
 * l'apercu (date, exercices, series) puis l'importe apres confirmation.
 *
 * Declaree dans le manifeste avec des intent-filter ACTION_VIEW (ouvrir un fichier) et
 * ACTION_SEND (partager un fichier ou un texte). Le contenu est lu tout de suite dans
 * onCreate() : l'autorisation de lecture donnee par l'autre appli ne vaut que pour cet
 * ecran. Rien n'est modifie tant que l'utilisateur n'a pas appuye sur "Importer".
 *
 * L'import passe par SessionImporter.importJsonWithDuplicateCheck() : copie automatique
 * avant l'import, et confirmation explicite si la seance a deja ete importee ce jour-la.
 * Toute la logique d'apercu est dans SessionPreview (testee en JUnit).
 */
public class ImportSessionActivity extends AppCompatActivity
{
    private TextView statusView;
    private TextView previewView;
    private MaterialButton confirmButton;
    private MaterialButton openAppButton;

    private String json;
    private SessionPreview preview;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_session);

        statusView = findViewById(R.id.tv_import_status);
        previewView = findViewById(R.id.tv_import_preview);
        confirmButton = findViewById(R.id.bt_import_confirm);
        openAppButton = findViewById(R.id.bt_import_open_app);

        findViewById(R.id.bt_import_close).setOnClickListener(v -> finish());
        confirmButton.setOnClickListener(v -> importSession());
        openAppButton.setText("Ouvrir " + AppNames.APP_NAME);
        openAppButton.setOnClickListener(v -> openApp());

        showPreview(getIntent());
    }

    private void showPreview(Intent intent)
    {
        try
        {
            json = readJson(intent);
        }
        catch (IOException | RuntimeException e)
        {
            showError("Impossible de lire le fichier : " + e.getMessage(), "");
            return;
        }

        ImportedSession session;
        try
        {
            session = SessionImporter.parseSession(json);
        }
        catch (SessionImporter.InvalidSessionException e)
        {
            showError(e.getMessage(), "");
            return;
        }

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        preview = SessionPreview.of(session, today, knownExerciseNames());

        String problem = preview.problem();
        if (problem != null)
        {
            showError(problem, preview.toText());
            return;
        }

        statusView.setText("Aperçu de la séance");
        previewView.setText(preview.toText());
        confirmButton.setVisibility(View.VISIBLE);
    }

    // Bouton "Importer". Tout est synchrone : si la seance a deja ete importee ce jour-la,
    // une alerte demande confirmation (sur "Annuler" rien ne change et le bouton reste
    // disponible) ; sinon onImportDone() est appele tout de suite.
    private void importSession()
    {
        SessionImporter.importJsonWithDuplicateCheck(
                this, json, MainActivity.dataStorage, preview.getDate(), this::onImportDone);
    }

    private void onImportDone(SessionImporter.Result result)
    {
        if (!result.success)
        {
            showError("Import impossible : " + result.errorMessage, preview.toText());
            return;
        }

        DataStorage.ImportSummary summary = result.summary;
        StringBuilder message = new StringBuilder();
        message.append(summary.setsImported).append(" série(s) importée(s) le ").append(summary.date);
        if (summary.exercisesCreated > 0)
        {
            message.append("\n").append(summary.exercisesCreated).append(" nouvel(s) exercice(s) créé(s)");
        }
        if (summary.setsSkipped > 0)
        {
            message.append("\n").append(summary.setsSkipped).append(" série(s) incomplète(s) ignorée(s)");
        }

        statusView.setTextColor(ContextCompat.getColor(this, R.color.completed_green));
        statusView.setText("Séance importée");
        previewView.setText(message.toString());
        confirmButton.setVisibility(View.GONE);
        openAppButton.setVisibility(View.VISIBLE);
    }

    // Ramene l'appli au premier plan sans empiler un nouvel ecran Workout (meme intent
    // que les notifications du minuteur, voir TabNavigation.resumeAppIntent()).
    private void openApp()
    {
        startActivity(TabNavigation.resumeAppIntent(this));
        finish();
    }

    private void showError(String message, String details)
    {
        statusView.setTextColor(ContextCompat.getColor(this, R.color.red));
        statusView.setText(message);
        previewView.setText(details);
        confirmButton.setVisibility(View.GONE);
    }

    // ACTION_VIEW : le fichier est dans data. ACTION_SEND : le fichier est dans
    // EXTRA_STREAM ; a defaut, certaines applis partagent le JSON comme TEXTE
    // (EXTRA_TEXT).
    private String readJson(Intent intent) throws IOException
    {
        Uri uri = null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action))
        {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null)
            {
                CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (text != null && text.length() > 0)
                {
                    return text.toString();
                }
            }
        }
        else if (Intent.ACTION_VIEW.equals(action))
        {
            uri = intent.getData();
        }

        if (uri == null)
        {
            throw new IOException("aucun fichier reçu");
        }
        return TextFiles.readAll(uri, this);
    }

    private static HashSet<String> knownExerciseNames()
    {
        HashSet<String> names = new HashSet<String>();
        for (Exercise exercise : MainActivity.dataStorage.getKnownExercises())
        {
            names.add(exercise.getName());
        }
        return names;
    }
}
