package com.example.verifit.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.verifit.AppNames;
import com.example.verifit.DataStorage;
import com.example.verifit.ImportedPlans;
import com.example.verifit.ImportedPlansStore;
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
 *
 * Import idempotent (15/10/2026) : si ce plan (meme contenu, voir ImportedPlans) a deja ete
 * importe, l'apercu le dit tout de suite, "Importer" disparait et seul un bouton secondaire
 * "Reimporter quand meme" reste (import force, sans seconde alerte). Ouvert aussi par l'icone
 * nuage de l'onglet Workout : plan du dossier Documents/FitEngine_plans (lu seulement) ou
 * fichier choisi par "Autre fichier...".
 */
public class ImportSessionActivity extends AppCompatActivity
{
    // Date de repli (AAAA-MM-JJ) pour un fichier sans "date", passee par l'onglet Workout
    // (jour affiche). Absente (fichier ouvert depuis une autre appli) : aujourd'hui.
    public static final String EXTRA_FALLBACK_DATE = "fallback_date";

    private TextView statusView;
    private TextView previewView;
    private MaterialButton confirmButton;
    private MaterialButton forceButton;
    private MaterialButton openAppButton;

    private String json;
    private String sourceName = "";
    private SessionPreview preview;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_session);

        statusView = findViewById(R.id.tv_import_status);
        previewView = findViewById(R.id.tv_import_preview);
        confirmButton = findViewById(R.id.bt_import_confirm);
        forceButton = findViewById(R.id.bt_import_force);
        // Lot S2 (revue securite du 27/10/2026) : cet ecran peut etre ouvert par n'importe
        // quelle appli. Un appui n'est pas pris en compte si une autre fenetre recouvre le
        // bouton (une appli malveillante ne peut pas faire appuyer sur "Importer" a ton insu).
        confirmButton.setFilterTouchesWhenObscured(true);
        forceButton.setFilterTouchesWhenObscured(true);
        openAppButton = findViewById(R.id.bt_import_open_app);

        findViewById(R.id.bt_import_close).setOnClickListener(v -> finish());
        confirmButton.setOnClickListener(v -> importSession());
        forceButton.setOnClickListener(v -> forceImportSession());
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

        String fallbackDate = getIntent().getStringExtra(EXTRA_FALLBACK_DATE);
        if (fallbackDate == null || fallbackDate.isEmpty())
        {
            fallbackDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        }
        preview = SessionPreview.of(session, fallbackDate, knownExerciseNames());

        String problem = preview.problem();
        if (problem != null)
        {
            showError(problem, preview.toText());
            return;
        }

        String header = header(session);
        ImportedPlans.Entry previous = ImportedPlansStore.load(this).find(ImportedPlans.hash(json));
        if (previous != null)
        {
            statusView.setTextColor(ContextCompat.getColor(this, R.color.red));
            statusView.setText("Plan déjà importé");
            previewView.setText(ImportedPlans.describePreviousImport(previous)
                    + "\nLe réimporter ajoutera ses séries une seconde fois.\n\n" + header + preview.toText());
            confirmButton.setVisibility(View.GONE);
            forceButton.setVisibility(View.VISIBLE);
            return;
        }

        statusView.setText("Aperçu de la séance");
        previewView.setText(header + preview.toText());
        confirmButton.setVisibility(View.VISIBLE);
    }

    // Libelle du plan (champ "comment", ex. "Cycle A - Force") et nom du fichier, au-dessus
    // de l'apercu.
    private String header(ImportedSession session)
    {
        StringBuilder text = new StringBuilder();
        String label = session == null ? "" : session.getComment().trim();
        if (!label.isEmpty())
        {
            text.append("Plan : ").append(label).append('\n');
        }
        if (!sourceName.isEmpty())
        {
            text.append("Fichier : ").append(sourceName).append('\n');
        }
        return text.toString();
    }

    // Bouton "Importer". Tout est synchrone : si la seance a deja ete importee ce jour-la,
    // une alerte demande confirmation (sur "Annuler" rien ne change et le bouton reste
    // disponible) ; sinon onImportDone() est appele tout de suite.
    private void importSession()
    {
        SessionImporter.importJsonWithDuplicateCheck(
                this, json, MainActivity.dataStorage, preview.getDate(), sourceName, this::onImportDone);
    }

    // Bouton "Reimporter quand meme" : l'avertissement est deja affiche dans l'apercu,
    // pas de seconde alerte.
    private void forceImportSession()
    {
        SessionImporter.importJsonForced(
                this, json, MainActivity.dataStorage, preview.getDate(), sourceName, this::onImportDone);
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
        if (result.ackWarning != null)
        {
            message.append("\n\n").append(result.ackWarning);
        }

        statusView.setTextColor(ContextCompat.getColor(this, R.color.completed_green));
        statusView.setText("Séance importée");
        previewView.setText(message.toString());
        confirmButton.setVisibility(View.GONE);
        forceButton.setVisibility(View.GONE);
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
        forceButton.setVisibility(View.GONE);
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
                if (text != null && text.length() > TextFiles.MAX_SESSION_CHARS)
                {
                    throw new IOException("texte trop long pour une séance");
                }
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
        // Lot S2 : seulement un fichier partage par une autre appli (content://). Un chemin
        // "file://" permettrait de designer n'importe quel fichier, y compris les donnees
        // privees de FitEngine ; aucune appli recente n'en envoie.
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme()))
        {
            throw new IOException("type de lien refusé (" + uri.getScheme() + "), seul un fichier partagé par une autre appli est accepté");
        }
        sourceName = TextFiles.displayName(uri, this);
        return TextFiles.readAll(uri, this, TextFiles.MAX_SESSION_CHARS);
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
