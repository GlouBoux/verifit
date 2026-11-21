package com.example.verifit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Textes de l'ecran de bilan de fin de seance (lot M5b.2, 21/11/2026) : PR proposes avec la
 * serie qui les justifie, objectifs du ou des plans compares, echecs, series hors plan, ce qui
 * sera ecrit au « Valider ». Classe pure (testee en JUnit), sans Android.
 */
public final class EngineEndOfSessionView
{
    private static final Pattern PLAN_LABEL =
            Pattern.compile("^Seance_(Force|Volume)_Cycle([A-Za-z0-9]+)_(\\d{4})-(\\d{2})-(\\d{2})_.*$");

    private EngineEndOfSessionView() {}

    // Poids a la francaise : « 52,5 kg », « 60 kg ».
    public static String kg(double value)
    {
        long rounded = Math.round(value);
        String text = Math.abs(value - rounded) < 1e-9
                ? String.valueOf(rounded)
                : String.format(Locale.FRANCE, "%.2f", value).replaceAll("0+$", "").replaceAll(",$", "");
        return text + " kg";
    }

    // « 2026-11-20 » -> « 20/11 », ou « 03/01/2026 » si l'annee n'est pas celle du bilan
    // (une serie ancienne du carnet peut justifier un PR : retour Romain du 21/11/2026).
    static String shortDate(String isoDate, String currentYear)
    {
        if (isoDate == null || isoDate.length() < 10)
        {
            return isoDate == null ? "?" : isoDate;
        }
        String day = isoDate.substring(8, 10) + "/" + isoDate.substring(5, 7);
        return isoDate.startsWith(currentYear) ? day : day + "/" + isoDate.substring(0, 4);
    }

    // « Seance_Force_CycleA_2026-11-19_10-00-00 » -> « Force, cycle A (plan du 19/11) ».
    public static String planTitle(String label)
    {
        Matcher m = PLAN_LABEL.matcher(label == null ? "" : label);
        if (!m.matches())
        {
            return label;
        }
        return m.group(1) + ", cycle " + m.group(2) + " (plan du " + m.group(5) + "/" + m.group(4) + ")";
    }

    public static String title(EngineEndOfSession.Result r)
    {
        return r.hasChanges() ? "Bilan de fin de séance" : "Bilan : rien de nouveau";
    }

    // timestamp : horodatage du bilan (« 2026-11-21T15:08:11 »), dont l'annee sert a dater
    // les series qui justifient les PR.
    public static String text(EngineEndOfSession.Result r, String timestamp)
    {
        StringBuilder s = new StringBuilder();
        prs(s, r, timestamp == null || timestamp.length() < 4 ? "" : timestamp.substring(0, 4));
        tracking(s, r);
        footer(s, r);
        return s.toString().trim();
    }

    private static void prs(StringBuilder s, EngineEndOfSession.Result r, String currentYear)
    {
        if (r.prs.isEmpty())
        {
            s.append("Aucun nouveau PR.\n\n");
            return;
        }
        s.append("NOUVEAUX PR PROPOSÉS (").append(r.prCount()).append(" RM, ")
                .append(r.prs.size()).append(r.prs.size() > 1 ? " exercices" : " exercice").append(")\n");
        for (EngineEndOfSession.ExercisePr p : r.prs)
        {
            s.append(p.exercise).append('\n');
            for (EngineEndOfSession.Change c : p.changes)
            {
                s.append("  RM ").append(c.rm).append(" : ")
                        .append(c.before == null ? "vide" : kg(c.before)).append(" → ").append(kg(c.after));
                if (c.justification == null)
                {
                    s.append(" (case vide remplie depuis les RM au-dessus)");
                }
                else
                {
                    s.append(" (").append(kg(c.justification.weight).replace(" kg", "")).append(" × ")
                            .append(c.justification.reps).append(" le ").append(shortDate(c.justification.date, currentYear))
                            .append(c.justification.cascade ? ", cascade" : "").append(')');
                }
                s.append('\n');
            }
        }
        s.append('\n');
    }

    private static void tracking(StringBuilder s, EngineEndOfSession.Result r)
    {
        String status = r.summaryStatus();
        if ("no_plan".equals(status))
        {
            s.append("Aucun plan en attente : seuls les PR sont vérifiés.\n\n");
            return;
        }
        if ("error".equals(status))
        {
            s.append("⚠ Suivi impossible : ").append(r.summaryError()).append("\n\n");
            return;
        }
        for (EngineEndOfSession.PlanReport plan : r.plans)
        {
            s.append(planTitle(plan.label).toUpperCase(Locale.FRANCE)).append('\n');
            int done = 0;
            int filet = 0;
            for (JsonObject row : plan.rows)
            {
                if ("battu".equals(str(row, "statut")))
                {
                    done++;
                    if (bool(row, "battu_par_filet"))
                    {
                        filet++;
                    }
                }
            }
            s.append("  Objectifs atteints : ").append(done).append('/').append(plan.rows.size());
            if (filet > 0)
            {
                s.append(" (dont ").append(filet).append(" par le filet)");
            }
            s.append(plan.complete ? ", plan terminé" : "").append('\n');
            for (JsonObject row : plan.rows)
            {
                s.append("  ").append(rowLine(row)).append('\n');
            }
            for (JsonObject hp : plan.unplanned)
            {
                s.append("  ").append(unplannedLine(hp)).append('\n');
            }
            for (String w : plan.warnings)
            {
                s.append("  ⚠ ").append(w).append('\n');
            }
            for (String w : plan.unplannedWarnings)
            {
                s.append("  ⚠ ").append(w).append('\n');
            }
            s.append('\n');
        }
    }

    // « ✓ Squat S1 : 90 kg × 8 », « ✗ Squat S2 : 80 kg × 10, tenu 7 », « · … pas encore tenté ».
    static String rowLine(JsonObject row)
    {
        String unit = "sec".equals(str(row, "unite")) ? " s" : "";
        String target = str(row, "exercice") + " " + str(row, "serie") + " : "
                + kg(num(row, "charge_prevue")) + " × " + row.get("rm").getAsInt() + unit;
        String statut = str(row, "statut");
        JsonElement attempt = row.get("tentative_reps");
        boolean tried = attempt != null && !attempt.isJsonNull();
        if ("battu".equals(statut))
        {
            return "✓ " + target + (bool(row, "battu_par_filet") ? ", rattrapé par le filet (tenu " + attempt.getAsInt() + ")" : "");
        }
        if (tried)
        {
            return "✗ " + target + ", tenu " + attempt.getAsInt() + unit + " (échec)";
        }
        if ("aucune_donnee".equals(statut))
        {
            return "· " + target + ", pas encore tenté (aucun PR à ce RM)";
        }
        return "· " + target + ", pas encore tenté";
    }

    // « + Hors plan : Squat 60 kg × 15, record ».
    static String unplannedLine(JsonObject row)
    {
        String unit = "sec".equals(str(row, "unite")) ? " s" : "";
        String statut = str(row, "statut");
        String verdict = "battu".equals(statut) ? "record" : "echec".equals(statut) ? "échec" : "sans record";
        return "+ Hors plan : " + str(row, "exercice") + " " + kg(num(row, "charge_prevue")) + " × "
                + row.get("tentative_reps").getAsInt() + unit + ", " + verdict;
    }

    private static void footer(StringBuilder s, EngineEndOfSession.Result r)
    {
        if (!r.hasChanges())
        {
            s.append("Rien de nouveau à enregistrer");
            s.append(r.alreadyWritten > 0 ? " : ce bilan a déjà été validé." : ".");
            return;
        }
        s.append("AU « VALIDER »\n");
        if (!r.prs.isEmpty())
        {
            s.append("  ").append(r.prCount()).append(" PR écrits, chacun tracé dans l'historique des PR\n");
        }
        s.append("  ").append(r.journal.size()).append(" ligne(s) ajoutée(s) au journal des séries");
        if (r.alreadyWritten > 0)
        {
            s.append(" (").append(r.alreadyWritten).append(" déjà écrite(s) à l'identique, ignorée(s))");
        }
        s.append('\n');
        List<String> removed = r.plansToRemove;
        if (!removed.isEmpty())
        {
            s.append("  Plan(s) qui sortent de l'attente :");
            for (String name : removed)
            {
                String label = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
                s.append("\n    ").append(planTitle(label)).append(r.superseded.contains(name) ? ", remplacé" : ", terminé");
            }
            s.append('\n');
        }
        s.append("  Copie automatique avant écriture. Rien n'est écrit si tu fermes.");
    }

    private static String str(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static double num(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? 0 : e.getAsDouble();
    }

    private static boolean bool(JsonObject o, String key)
    {
        JsonElement e = o.get(key);
        return e != null && !e.isJsonNull() && e.getAsBoolean();
    }
}
