package com.example.verifit.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.verifit.R;
import com.example.verifit.SessionImporter;
import com.example.verifit.SessionPreview;
import com.example.verifit.TextFiles;
import com.example.verifit.model.Exercise;
import com.example.verifit.model.ImportedSession;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

/**
 * "Ouvrir avec FitEngine" (lot D, etape D7, 02/10/2026) : recoit une seance JSON envoyee
 * par une autre appli (Drive, mail, Discord, fichier telecharge...) et en montre l'apercu
 * (date, exercices, series) avant tout import.
 *
 * Declaree dans le manifeste avec des intent-filter ACTION_VIEW (ouvrir un fichier) et
 * ACTION_SEND (partager un fichier ou un texte). Le contenu est lu tout de suite dans
 * onCreate() : l'autorisation de lecture donnee par l'autre appli ne vaut que pour cet
 * ecran. Ne modifie jamais les donnees (l'import vient a l'etape suivante).
 *
 * Toute la logique d'apercu est dans SessionPreview (testee en JUnit).
 */
public class ImportSessionActivity extends AppCompatActivity
{
    private TextView statusView;
    private TextView previewView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_session);

        statusView = findViewById(R.id.tv_import_status);
        previewView = findViewById(R.id.tv_import_preview);
        findViewById(R.id.bt_import_close).setOnClickListener(v -> finish());

        showPreview(getIntent());
    }

    private void showPreview(Intent intent)
    {
        String json;
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
        SessionPreview preview = SessionPreview.of(session, today, knownExerciseNames());

        String problem = preview.problem();
        if (problem != null)
        {
            showError(problem, preview.toText());
            return;
        }

        statusView.setText("Aperçu de la séance");
        previewView.setText(preview.toText());
    }

    private void showError(String message, String details)
    {
        statusView.setTextColor(ContextCompat.getColor(this, R.color.red));
        statusView.setText(message);
        previewView.setText(details);
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
