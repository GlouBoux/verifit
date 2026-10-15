package com.example.verifit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Choix des plans proposes par le bouton "Importer la seance en attente" (session du
 * 15/10/2026) : parmi les fichiers du dossier Documents/FitEngine_plans (premier niveau
 * seulement), le plan le plus recent de CHAQUE type de seance (Force, Volume...).
 *
 * Nom attendu, ecrit par generate_workout.py (depot Coaching) :
 *   Seance_<Type>_<Cycle>_<AAAA-MM-JJ>_<HH-MM-SS>.json   ex. Seance_Force_CycleA_2026-10-15_19-39-29.json
 * Le "plus recent" est celui dont l'horodatage DU NOM est le plus grand (heure de
 * generation sur le PC), pas la date de modification du fichier : Syncthing peut livrer
 * les fichiers dans n'importe quel ordre. Date de modification seulement pour departager
 * deux noms au meme horodatage.
 *
 * Ignores (et comptes, pour le dire a l'utilisateur) : les dossiers (.stfolder de
 * Syncthing...), les fichiers caches ou temporaires (".", "~"), tout fichier qui n'est pas
 * un .json, et les .json au nom non reconnu. Rien n'est jamais supprime ni deplace :
 * c'est le PC qui fait le menage dans ce dossier.
 *
 * Classe sans dependance Android (testee en JUnit). La lecture du dossier est dans
 * PlanFolder.
 */
public final class PendingPlans
{
    // Seance_<Type>_<Cycle>_<AAAA-MM-JJ_HH-MM-SS>.json ; Type et Cycle sans "_".
    private static final Pattern PLAN_NAME = Pattern.compile(
            "^Seance_([^_]+)_([^_]+)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.json$");

    // Un element du dossier, tel que le liste le fournisseur de documents. H = identifiant
    // du fichier (un Uri sur Android, une chaine dans les tests).
    public static final class FileEntry<H>
    {
        public final H handle;
        public final String name;
        public final long lastModified;
        public final boolean isDirectory;

        public FileEntry(H handle, String name, long lastModified, boolean isDirectory)
        {
            this.handle = handle;
            this.name = name == null ? "" : name;
            this.lastModified = lastModified;
            this.isDirectory = isDirectory;
        }
    }

    // Un plan reconnu.
    public static final class Plan<H>
    {
        public final H handle;
        public final String fileName;
        public final String type;          // "Force", "Volume"...
        public final String cycle;         // "CycleA"...
        public final String generatedAt;   // "2026-10-15_19-39-29", tel que dans le nom
        public final long lastModified;

        Plan(H handle, String fileName, String type, String cycle, String generatedAt, long lastModified)
        {
            this.handle = handle;
            this.fileName = fileName;
            this.type = type;
            this.cycle = cycle;
            this.generatedAt = generatedAt;
            this.lastModified = lastModified;
        }

        // "15/10/2026 19:39", heure de generation sur le PC.
        public String generatedAtLabel()
        {
            return generatedAt.substring(8, 10) + "/" + generatedAt.substring(5, 7) + "/" + generatedAt.substring(0, 4)
                    + " " + generatedAt.substring(11, 13) + ":" + generatedAt.substring(14, 16);
        }
    }

    // Resultat : le plus recent par type, du plus recent au plus ancien.
    public static final class Selection<H>
    {
        public final List<Plan<H>> latestPerType;
        public final int olderPlans;       // plans reconnus mais plus anciens qu'un autre du meme type
        public final int ignoredFiles;     // fichiers non reconnus (hors dossiers et fichiers caches)

        Selection(List<Plan<H>> latestPerType, int olderPlans, int ignoredFiles)
        {
            this.latestPerType = Collections.unmodifiableList(latestPerType);
            this.olderPlans = olderPlans;
            this.ignoredFiles = ignoredFiles;
        }

        public boolean isEmpty()
        {
            return latestPerType.isEmpty();
        }
    }

    private PendingPlans() {}

    // Plan reconnu a partir du nom, ou null.
    public static <H> Plan<H> parse(FileEntry<H> entry)
    {
        if (entry == null || entry.isDirectory)
        {
            return null;
        }
        Matcher m = PLAN_NAME.matcher(entry.name);
        if (!m.matches())
        {
            return null;
        }
        return new Plan<H>(entry.handle, entry.name, m.group(1), m.group(2), m.group(3), entry.lastModified);
    }

    public static <H> Selection<H> select(List<FileEntry<H>> entries)
    {
        Map<String, Plan<H>> latest = new LinkedHashMap<String, Plan<H>>();
        int recognised = 0;
        int ignored = 0;

        for (FileEntry<H> entry : entries)
        {
            if (entry == null || entry.isDirectory || entry.name.startsWith(".") || entry.name.startsWith("~"))
            {
                continue;
            }
            Plan<H> plan = parse(entry);
            if (plan == null)
            {
                ignored++;
                continue;
            }
            recognised++;
            Plan<H> current = latest.get(plan.type);
            if (current == null || isNewer(plan, current))
            {
                latest.put(plan.type, plan);
            }
        }

        ArrayList<Plan<H>> result = new ArrayList<Plan<H>>(latest.values());
        Collections.sort(result, new Comparator<Plan<H>>()
        {
            @Override
            public int compare(Plan<H> a, Plan<H> b)
            {
                return isNewer(a, b) ? -1 : (isNewer(b, a) ? 1 : 0);
            }
        });
        return new Selection<H>(result, recognised - result.size(), ignored);
    }

    // Horodatage du nom (format a largeur fixe : l'ordre alphabetique est l'ordre
    // chronologique), puis date de modification, puis nom (resultat stable).
    static boolean isNewer(Plan<?> a, Plan<?> b)
    {
        int byName = a.generatedAt.compareTo(b.generatedAt);
        if (byName != 0)
        {
            return byName > 0;
        }
        if (a.lastModified != b.lastModified)
        {
            return a.lastModified > b.lastModified;
        }
        return a.fileName.compareTo(b.fileName) > 0;
    }
}
