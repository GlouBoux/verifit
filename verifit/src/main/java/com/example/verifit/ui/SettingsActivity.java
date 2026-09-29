package com.example.verifit.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.example.verifit.BackupManager;
import com.example.verifit.BuildConfig;
import com.example.verifit.R;
import com.example.verifit.ThemeHelper;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }


    public static class SettingsFragment extends PreferenceFragmentCompat implements PreferenceManager.OnPreferenceTreeClickListener{

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Retour Romain 28/09/2026 : "un numero de build [...] pour verifier qu'on
            // parle bien de la meme version". BuildConfig.BUILD_TIMESTAMP est genere
            // automatiquement a chaque build (voir build.gradle, defaultConfig) - pas
            // de geste manuel a oublier, contrairement a versionName qu'il faut
            // incrementer soi-meme.
            Preference versionPreference = findPreference("version");
            if (versionPreference != null) {
                versionPreference.setSummary(getBuildInfo());
            }

            // Retour Romain 16/09/2026 : "le bouton Light/Dark/Système dans les
            // Réglages, comme FitNotes". La ListPreference "theme" (root_preferences.xml)
            // persiste deja sa valeur toute seule (comportement par defaut d'une
            // Preference) ; on applique juste le changement immediatement, sans attendre
            // un redemarrage de l'app - voir ThemeHelper pour le detail des modes, et
            // VerifitApplication pour l'application au demarrage du process.
            Preference themePreference = findPreference("theme");
            if (themePreference != null) {
                themePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        ThemeHelper.applyTheme(newValue.toString());
                        return true;
                    }
                });
            }
        }


        @Override
        public boolean onPreferenceTreeClick(Preference preference) {
            String key = preference.getKey();

            // Backup & Restore
            if (key.equals("importcsv"))
            {
                // Prepare to show exercise dialog box
                LayoutInflater inflater = LayoutInflater.from(getContext());
                View view = inflater.inflate(R.layout.import_warning_dialog, null);
                AlertDialog alertDialog = new AlertDialog.Builder(getContext()).setView(view).create();



                Button bt_yes3 = view.findViewById(R.id.bt_yes3);
                Button bt_no3 = view.findViewById(R.id.bt_no3);

                bt_yes3.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent in = new Intent(getActivity(), MainActivity.class);
                        // Retour a l'ecran Workout existant (recree avec l'action demandee) plutot
                        // qu'une instance de plus dans la pile (voir TabNavigation).
                        in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        in.putExtra("doit", "importcsv");
                        startActivity(in);
                    }
                });

                bt_no3.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        alertDialog.dismiss();
                    }
                });
                alertDialog.show();
            }
            else if (key.equals("exportcsv"))
            {
                Intent in = new Intent(getActivity(), MainActivity.class);
                // Retour a l'ecran Workout existant (recree avec l'action demandee) plutot
                // qu'une instance de plus dans la pile (voir TabNavigation).
                in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                in.putExtra("doit", "exportcsv");
                startActivity(in);
            }
            // Export JSON dedie au pipeline Coaching (retour Romain 24/09/2026, voir
            // claude/verifit-migration-plan.md story 2.2) - meme mecanique que
            // "exportcsv" ci-dessus.
            else if (key.equals("exportjson"))
            {
                Intent in = new Intent(getActivity(), MainActivity.class);
                // Retour a l'ecran Workout existant (recree avec l'action demandee) plutot
                // qu'une instance de plus dans la pile (voir TabNavigation).
                in.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                in.putExtra("doit", "exportjson");
                startActivity(in);
            }
            // Backup complet (point 1.4 de la revue du 28/09/2026, voir BackupManager)
            else if (key.equals("exportfullbackup"))
            {
                BackupManager.exportToDocuments(getContext(), MainActivity.dataStorage);
            }
            else if (key.equals("importfullbackup"))
            {
                // "*/*" : un .json n'est pas toujours annonce comme application/json par
                // le selecteur ; le contenu est de toute facon valide avant restauration.
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
                {
                    intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, BackupManager.documentsFolderUri());
                }
                startActivityForResult(intent, FULL_BACKUP_REQUEST_CODE);
            }
            else if (key.equals("deletedata"))
            {
                deleteData();
            }

            // General
            // "theme" (Light/Dark/Système) : plus besoin de cas ici - c'est une
            // ListPreference standard, elle affiche son propre dialogue de choix toute
            // seule. Voir le OnPreferenceChangeListener pose dans onCreatePreferences()
            // pour l'application immediate du changement (ThemeHelper).
            else if (key.equals("version"))
            {
                // Retour Romain 28/09/2026 : copie le numero de build dans le
                // presse-papier au tap, pratique pour me le communiquer tel quel sans
                // recopier a la main.
                String buildInfo = getBuildInfo();
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("verifit version", buildInfo);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(getContext(), "Copied: " + buildInfo, Toast.LENGTH_SHORT).show();
            }
            // Licence GPL v3 heritee du projet d'origine : notice gardee volontairement.
            else if (key.equals("licence"))
            {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.en.html"));
                startActivity(browserIntent);
            }

            return true;
        }


        private static final int FULL_BACKUP_REQUEST_CODE = 91;

        // Fichier choisi pour "Restaurer un backup complet" : lecture + validation SANS
        // rien modifier, puis confirmation explicite (date et contenu du backup) avant de
        // remplacer les donnees.
        private void onFullBackupFileChosen(Uri uri)
        {
            final BackupManager.FullBackup backup;
            try
            {
                backup = BackupManager.readFromUri(getContext(), uri);
            }
            catch (IOException | IllegalArgumentException e)
            {
                Toast.makeText(getContext(), "Restauration annulée : " + e.getMessage() + ". Tes données n'ont pas été modifiées.", Toast.LENGTH_LONG).show();
                return;
            }

            String exportedAt = backup.getExportedAt() == null ? "date inconnue" : backup.getExportedAt().replace('T', ' ');
            new AlertDialog.Builder(getContext())
                    .setTitle("Restaurer ce backup ?")
                    .setMessage("Backup du " + exportedAt + " : " + backup.getWorkoutDays().size() + " jours, "
                            + backup.countSets() + " séries, " + backup.getKnownExercises().size() + " exercices, "
                            + backup.getGoals().size() + " objectifs.\n\nToutes les données actuelles seront remplacées. "
                            + "Une copie de l'état actuel est d'abord enregistrée dans Documents/Verifit/auto.")
                    .setPositiveButton("Restaurer", (dialog, which) -> {
                        BackupManager.restore(getContext(), MainActivity.dataStorage, backup);
                        Toast.makeText(getContext(), "Backup restauré", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
        }

        // When File explorer stops this function runs
        @Override
        public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
        {
            super.onActivityResult(requestCode, resultCode, data);

            if (resultCode == Activity.RESULT_OK)
            {
                if (data != null)
                {
                    Uri uri = data.getData();
                    if (requestCode == FULL_BACKUP_REQUEST_CODE)
                    {
                        onFullBackupFileChosen(uri);
                    }
                }
            }
        }


        // Delete all currently saved workout data
        public void deleteData()
        {
            // Prepare to show exercise dialog box
            LayoutInflater inflater = LayoutInflater.from(getContext());
            View view = inflater.inflate(R.layout.delete_all_dialog,null);
            AlertDialog alertDialog = new AlertDialog.Builder(getContext()).setView(view).create();

            Button bt_yes = view.findViewById(R.id.bt_yes3);
            Button bt_no = view.findViewById(R.id.bt_no3);


            bt_yes.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view)
                {
                    MainActivity.dataStorage.deleteAllWorkouts(getContext());
                    alertDialog.dismiss();
                    Toast.makeText(getContext(),"Data Deleted",Toast.LENGTH_SHORT).show();
                    Intent in = new Intent(getContext(),MainActivity.class);
                    startActivity(in);
                }
            });

            bt_no.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view) {
                    alertDialog.dismiss();
                }
            });


            // Show Exercise Dialog Box
            alertDialog.show();
        }

        // Retour Romain 28/09/2026 : versionName (ex. "1.0.14") + horodatage du build
        // (ex. "2026-09-28 19:42"), genere automatiquement par Gradle a chaque build -
        // voir BuildConfig.BUILD_TIMESTAMP dans build.gradle. Utilise a la fois pour le
        // summary affiche dans les Settings et pour la copie presse-papier au tap.
        public String getBuildInfo()
        {
            SimpleDateFormat buildDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String buildDate = buildDateFormat.format(new Date(BuildConfig.BUILD_TIMESTAMP));
            return BuildConfig.VERSION_NAME + " - build " + buildDate;
        }
    }
}