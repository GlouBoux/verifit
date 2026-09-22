package com.example.verifit;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.MainActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

// Panneau (bottom sheet) de commentaire d'UNE serie - remplace le petit dialogue
// add_exercise_comment_dialog, dupliqué jusqu'ici dans WorkoutSetAdapter et
// AddExerciseWorkoutSetAdapter (retour Romain 21/09/2026, voir
// claude/verifit-commentaires-serie-propositions.md).
//
// Deux blocs distincts :
// - "Plan" : WorkoutSet.getPlanComment(), le texte pre-rempli par le script generateur,
//   en LECTURE SEULE, affiche en entier (un segment par ligne, voir formatPlan()).
// - "My note" : WorkoutSet.getComment(), le champ multiligne que Romain edite.
//   "Clear" ne vide que ce champ (il faut ensuite "Save" pour persister, comme l'ancien
//   dialogue) - le plan n'est jamais modifie ni efface depuis ce panneau.
//
// Le clavier ne s'ouvre tout seul QUE si la serie n'a pas de plan : avec un plan, Romain
// vient souvent le LIRE avant la serie et un clavier ouvert en masquerait la moitie.
public class SetCommentSheet
{
    // Appele apres un "Save" reussi, pour que l'adapter rafraichisse la ligne (icone).
    public interface OnSavedListener
    {
        void onSaved();
    }

    // Separateur des segments du commentaire genere par workout_engine.format_pr_gain()
    // ("S1 Ancrage — nouveau PR estime ... — theorique ... — filet 2 reps"). Le format
    // est un contrat cote Coaching (pr_tracking._parse_set_comment decoupe sur le meme
    // " — ") : on ne fait ici que l'AFFICHER autrement, jamais le modifier.
    private static final String PLAN_SEPARATOR = " — ";

    public static void show(final Context ct, final WorkoutSet set, final OnSavedListener listener)
    {
        if (set == null)
        {
            return;
        }

        final BottomSheetDialog dialog = new BottomSheetDialog(ct);
        View view = LayoutInflater.from(ct).inflate(R.layout.set_comment_sheet, null);
        dialog.setContentView(view);

        TextView subtitle = view.findViewById(R.id.tv_sheet_subtitle);
        TextView planLabel = view.findViewById(R.id.tv_plan_label);
        TextView planText = view.findViewById(R.id.tv_plan_text);
        View planDivider = view.findViewById(R.id.v_plan_divider);
        final EditText noteInput = view.findViewById(R.id.et_set_note);
        MaterialButton saveButton = view.findViewById(R.id.bt_save_set_note);
        MaterialButton clearButton = view.findViewById(R.id.bt_clear_set_note);

        subtitle.setText(buildSubtitle(set));

        boolean hasPlan = set.hasPlanComment();
        if (hasPlan)
        {
            planText.setText(formatPlan(set.getPlanComment()));
        }
        else
        {
            planLabel.setVisibility(View.GONE);
            planText.setVisibility(View.GONE);
            planDivider.setVisibility(View.GONE);
        }

        // Meme garde que le dialogue d'origine : d'anciennes sauvegardes ont pu ecrire la
        // chaine "null" a la place d'un vrai null.
        if (set.hasNote())
        {
            noteInput.setText(set.getComment());
            // Curseur en fin de note, pret a completer (avant : au debut, sur un champ
            // d'une seule ligne qui defilait).
            noteInput.setSelection(noteInput.getText().length());
        }

        clearButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                noteInput.setText("");
            }
        });

        saveButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                set.setComment(noteInput.getText().toString().trim());

                // Let the backup service know something changed, same as every other
                // mutation in the app.
                MainActivity.autoBackupRequired = true;
                com.example.verifit.SharedPreferences sharedPreferences = new com.example.verifit.SharedPreferences(ct);
                sharedPreferences.save("true", "autoBackupRequired");

                MainActivity.dataStorage.saveWorkoutData(ct);

                if (listener != null)
                {
                    listener.onSaved();
                }
                Toast.makeText(ct, "Comment saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        // Panneau deplie d'emblee (pas de position "peek" a moitie masquee), et fond du
        // conteneur natif transparent pour laisser apparaitre set_comment_sheet_bg
        // (coins arrondis, couleur de surface des dialogues de l'app).
        dialog.setOnShowListener(new DialogInterface.OnShowListener()
        {
            @Override
            public void onShow(DialogInterface dialogInterface)
            {
                FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                if (bottomSheet != null)
                {
                    bottomSheet.setBackgroundColor(Color.TRANSPARENT);
                    BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                    behavior.setSkipCollapsed(true);
                    behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }
        });

        if (dialog.getWindow() != null)
        {
            dialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | (hasPlan
                        ? WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                        : WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE));
        }
        if (!hasPlan)
        {
            noteInput.requestFocus();
        }

        dialog.show();
    }

    // Icone de commentaire d'une ligne de serie (commune a WorkoutSetAdapter et
    // AddExerciseWorkoutSetAdapter, meme layout workout_set_row.xml) - deux etats lisibles
    // d'un coup d'oeil (retour Romain 21/09/2026) :
    // - colorPrimary (bleu) : la serie a une NOTE perso ecrite par Romain ;
    // - gris discret (core_grey_40) : la serie n'a qu'un PLAN du script (ou, avec
    //   visibleEvenIfEmpty, aucun commentaire du tout - voir ci-dessous).
    // Masquee (GONE) sans plan ni note, sauf visibleEvenIfEmpty : sert a
    // AddExerciseWorkoutSetAdapter pour la serie EN COURS D'EDITION, afin de pouvoir y
    // ajouter une note sans passer par le commentaire d'exercice de la barre d'outils.
    // La colonne du badge est a position fixe (Guideline), donc GONE/VISIBLE ne decale
    // jamais les autres colonnes de la ligne.
    public static void bindIndicator(Context ct, ImageView icon, WorkoutSet set, boolean visibleEvenIfEmpty)
    {
        boolean hasNote = set.hasNote();
        boolean hasPlan = set.hasPlanComment();

        icon.setVisibility((hasNote || hasPlan || visibleEvenIfEmpty) ? View.VISIBLE : View.GONE);

        int colorRes = hasNote ? R.color.colorPrimary : R.color.core_grey_40;
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(ContextCompat.getColor(ct, colorRes)));
    }

    // "Assisted HSPU PW  ·  26.0 kg x 5 reps" - rappelle de quelle serie il s'agit, le
    // panneau masquant la liste.
    private static String buildSubtitle(WorkoutSet set)
    {
        StringBuilder sb = new StringBuilder();
        if (set.getExerciseName() != null)
        {
            sb.append(set.getExerciseName());
        }
        if (set.getWeight() != null && set.getReps() != null)
        {
            if (sb.length() > 0)
            {
                sb.append("  ·  ");
            }
            sb.append(set.getWeight()).append(" kg x ").append((int) Math.round(set.getReps())).append(" reps");
        }
        return sb.toString();
    }

    // Un segment par ligne, le premier ("S1 Ancrage", "Pivot 8->9 reps"...) en gras pour
    // repérer d'un coup d'oeil le type de serie. Un texte sans separateur (ex.
    // "Echauffement") s'affiche tel quel, sans gras.
    private static CharSequence formatPlan(String plan)
    {
        String[] segments = plan.split(PLAN_SEPARATOR);
        if (segments.length <= 1)
        {
            return plan;
        }

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < segments.length; i++)
        {
            if (i > 0)
            {
                joined.append('\n');
            }
            joined.append(segments[i].trim());
        }

        SpannableStringBuilder styled = new SpannableStringBuilder(joined);
        int firstLineEnd = segments[0].trim().length();
        if (firstLineEnd > 0)
        {
            styled.setSpan(new StyleSpan(Typeface.BOLD), 0, firstLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return styled;
    }
}
