package com.example.verifit;

import com.example.verifit.model.WorkoutSet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

// Calculs de l'onglet Graph d'un exercice (ExerciseGraphActivity), isoles de tout code
// Android pour pouvoir etre testes en JUnit simple (voir ExerciseGraphDataTest).
//
// Retour Romain 06/10/2026 : "l'onglet graph est inutilisable actuellement [...] reprend ce
// que fait FitNotes (sans la totalite). Estimated 1RM, PR, max weight for rep [...] l'idee
// est que je puisse suivre sur l'app que je progresse bien dans le temps sur un exercice et
// idealement identifier ou ca peche". Quatre vues :
//  - E1RM : meilleur 1RM estime (formule Epley de WorkoutSet.getEplayOneRepMax(), la meme
//    que l'ecran Personal Records) par seance ;
//  - MAX_WEIGHT_FOR_REPS : pour un nombre de reps N, poids max par seance (peut baisser) ;
//  - PERSONAL_RECORDS : pour un nombre de reps N, l'escalier des records (ne baisse jamais),
//    issu de DataStorage.calculateRepRangeHistory() donc identique a l'ecran trophee ;
//  - REP_PROFILE : pour chaque nombre de reps, record absolu vs meilleur sur la periode
//    choisie, pour reperer les zones qui stagnent ("ou ca peche").
//
// Convention de transitivite reprise telle quelle de l'ecran PR : une serie de R reps compte
// pour tout N <= R (si on a reussi R reps, on a pu en faire N). Un point obtenu ainsi est dit
// "deduit" (sourceReps != reps demande).
//
// Les series sans charge positive (poids de corps, tenues) sont ignorees : e1RM et poids max
// n'ont pas de sens a 0 kg.
public final class ExerciseGraphData
{
    private ExerciseGraphData() {}

    public static final String[] PERIOD_LABELS = {"1m", "3m", "6m", "1y", "all"};
    // Duree en jours de chaque periode (0 = tout l'historique). Mois approximes (30 j).
    public static final int[] PERIOD_DAYS = {30, 91, 182, 365, 0};

    // Seuil sous lequel un meilleur recent est juge "en retrait" du record dans le profil.
    public static final double WEAK_GAP_PCT = 2.0;

    // ------------------------------------------------------------------ modeles

    public static class Session
    {
        public final long day;
        public final String date;
        public final ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();

        Session(long day, String date)
        {
            this.day = day;
            this.date = date;
        }
    }

    // Un point de courbe (une seance, ou une marche de l'escalier des records).
    public static class Point
    {
        public final long day;
        public final String date;
        public final double value;
        // Serie qui a produit la valeur.
        public final int reps;
        public final double weight;
        // Vrai si la serie source a plus de reps que le nombre demande (transitivite).
        public final boolean deduced;
        // Nombre de series de la seance (0 si non applicable).
        public final int setCount;
        // Vrai si ce point bat tout ce qui precede dans la serie (record).
        public boolean record;
        // Point technique (prolongement jusqu'a aujourd'hui, niveau au debut de la periode) :
        // trace dans la ligne, jamais selectionnable.
        public final boolean extension;

        public Point(long day, String date, double value, int reps, double weight,
                     boolean deduced, int setCount, boolean extension)
        {
            this.day = day;
            this.date = date;
            this.value = value;
            this.reps = reps;
            this.weight = weight;
            this.deduced = deduced;
            this.setCount = setCount;
            this.extension = extension;
        }
    }

    // Une ligne du profil par reps.
    public static class ProfileRow
    {
        public int reps;
        public double recordWeight;
        public String recordDate;
        public boolean recordDeduced;
        public int recordSourceReps;
        public long daysSinceRecord;
        // NaN si rien a ce nombre de reps (ou plus) sur la periode.
        public double recentWeight = Double.NaN;
        public String recentDate;
        // recent / record - 1, en %. NaN si pas de recent.
        public double gapPct = Double.NaN;

        public boolean worked()
        {
            return !Double.isNaN(recentWeight);
        }
    }

    // ------------------------------------------------------------------ dates

    // "yyyy-MM-dd" (ou "yyyy-MM-dd HH:mm...") -> jours depuis 1970-01-01. -1 si illisible
    // (aucune date reelle de l'app n'est avant 1970, donc -1 ne cohabite avec aucune date).
    public static long epochDay(String date)
    {
        if (date == null || date.length() < 10 || date.charAt(4) != '-' || date.charAt(7) != '-')
        {
            return -1;
        }
        int y, m, d;
        try
        {
            y = Integer.parseInt(date.substring(0, 4));
            m = Integer.parseInt(date.substring(5, 7));
            d = Integer.parseInt(date.substring(8, 10));
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
        if (m < 1 || m > 12 || d < 1 || d > 31)
        {
            return -1;
        }
        // Algorithme "days from civil" (H. Hinnant) : pas de fuseau, pas d'API recente
        // (java.time n'est pas disponible avant l'API 26, minSdk = 16).
        long yy = m <= 2 ? y - 1 : y;
        long era = (yy >= 0 ? yy : yy - 399) / 400;
        long yoe = yy - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    public static long todayEpochDay()
    {
        long now = System.currentTimeMillis();
        long local = now + TimeZone.getDefault().getOffset(now);
        // Division simple : Math.floorDiv(long, long) n'existe qu'a partir de l'API 24 et
        // l'epoch local est de toute facon positif.
        return local / 86400000L;
    }

    public static String formatDay(long epochDay, boolean withYear, Locale locale)
    {
        SimpleDateFormat f = new SimpleDateFormat(withYear ? "d MMM yyyy" : "d MMM", locale);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(epochDay * 86400000L));
    }

    // Axe X : "mois annee" quand la plage depasse un an, sinon "jour mois".
    public static String formatAxisDay(long epochDay, boolean longSpan, Locale locale)
    {
        SimpleDateFormat f = new SimpleDateFormat(longSpan ? "MMM yy" : "d MMM", locale);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(epochDay * 86400000L));
    }

    // ------------------------------------------------------------------ seances

    private static int roundReps(WorkoutSet s)
    {
        return (int) Math.round(s.getReps());
    }

    private static boolean usable(WorkoutSet s)
    {
        return s != null && s.getReps() != null && s.getWeight() != null
                && s.getReps() > 0.0 && s.getWeight() > 0.0 && epochDay(s.getDate()) >= 0;
    }

    // Regroupe les series de l'exercice par date (ordre chronologique), en ecartant celles
    // qui n'ont ni reps ni charge positive ni date lisible.
    public static ArrayList<Session> buildSessions(List<WorkoutSet> sets)
    {
        TreeMap<String, Session> byDate = new TreeMap<String, Session>();
        for (WorkoutSet s : sets)
        {
            if (!usable(s))
            {
                continue;
            }
            String date = s.getDate().substring(0, 10);
            Session session = byDate.get(date);
            if (session == null)
            {
                session = new Session(epochDay(date), date);
                byDate.put(date, session);
            }
            session.sets.add(s);
        }
        return new ArrayList<Session>(byDate.values());
    }

    // Nombres de reps effectivement realises (arrondis), croissants, sans doublon.
    public static ArrayList<Integer> performedRepCounts(List<Session> sessions)
    {
        java.util.TreeSet<Integer> counts = new java.util.TreeSet<Integer>();
        for (Session session : sessions)
        {
            for (WorkoutSet s : session.sets)
            {
                counts.add(roundReps(s));
            }
        }
        return new ArrayList<Integer>(counts);
    }

    // Nombre de reps propose par defaut : le plus frequent sur les 10 dernieres seances
    // (a egalite, le plus eleve). -1 si aucune seance.
    public static int defaultReps(List<Session> sessions)
    {
        HashMap<Integer, Integer> counts = new HashMap<Integer, Integer>();
        int from = Math.max(0, sessions.size() - 10);
        for (int i = from; i < sessions.size(); i++)
        {
            for (WorkoutSet s : sessions.get(i).sets)
            {
                int r = roundReps(s);
                Integer c = counts.get(r);
                counts.put(r, c == null ? 1 : c + 1);
            }
        }
        int best = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet())
        {
            if (e.getValue() > bestCount || (e.getValue() == bestCount && e.getKey() > best))
            {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ series

    // Marque comme "record" tout point strictement superieur a tous les precedents (le
    // premier point n'est pas un record : il n'a rien battu).
    private static void markRunningRecords(List<Point> points)
    {
        double best = Double.NEGATIVE_INFINITY;
        boolean first = true;
        for (Point p : points)
        {
            if (!first && p.value > best)
            {
                p.record = true;
            }
            best = Math.max(best, p.value);
            first = false;
        }
    }

    // Meilleur 1RM estime de chaque seance.
    public static ArrayList<Point> e1rmSeries(List<Session> sessions)
    {
        ArrayList<Point> points = new ArrayList<Point>();
        for (Session session : sessions)
        {
            WorkoutSet best = null;
            for (WorkoutSet s : session.sets)
            {
                if (best == null || s.getEplayOneRepMax() > best.getEplayOneRepMax())
                {
                    best = s;
                }
            }
            if (best != null)
            {
                points.add(new Point(session.day, session.date, best.getEplayOneRepMax(),
                        roundReps(best), best.getWeight(), false, session.sets.size(), false));
            }
        }
        markRunningRecords(points);
        return points;
    }

    // Poids max de chaque seance pour au moins `reps` repetitions. A poids egal, la serie
    // dont le nombre de reps est le plus proche de `reps` est retenue (donc une serie
    // exacte avant une serie deduite).
    public static ArrayList<Point> maxWeightForReps(List<Session> sessions, int reps)
    {
        ArrayList<Point> points = new ArrayList<Point>();
        for (Session session : sessions)
        {
            WorkoutSet best = null;
            for (WorkoutSet s : session.sets)
            {
                int r = roundReps(s);
                if (r < reps)
                {
                    continue;
                }
                if (best == null || s.getWeight() > best.getWeight()
                        || (s.getWeight().equals(best.getWeight()) && r < roundReps(best)))
                {
                    best = s;
                }
            }
            if (best != null)
            {
                int bestReps = roundReps(best);
                points.add(new Point(session.day, session.date, best.getWeight(), bestReps,
                        best.getWeight(), bestReps != reps, session.sets.size(), false));
            }
        }
        markRunningRecords(points);
        return points;
    }

    // Escalier des records pour `reps`, d'apres l'historique de DataStorage (une marche par
    // date : si plusieurs records tombent le meme jour, le plus haut gagne). Prolonge
    // jusqu'a `today` par un point technique pour montrer depuis combien de temps le
    // record tient.
    public static ArrayList<Point> recordSteps(Map<Integer, ? extends List<RepRangePREvent>> history,
                                               int reps, long today)
    {
        ArrayList<Point> points = new ArrayList<Point>();
        List<RepRangePREvent> events = history == null ? null : history.get(reps);
        if (events == null || events.isEmpty())
        {
            return points;
        }
        for (RepRangePREvent ev : events)
        {
            long day = epochDay(ev.getDate());
            if (day < 0)
            {
                continue;
            }
            Point p = new Point(day, ev.getDate().substring(0, 10), ev.getWeight(),
                    ev.getSourceReps(), ev.getWeight(), ev.isDeduced(), 0, false);
            p.record = true;
            if (!points.isEmpty() && points.get(points.size() - 1).day == day)
            {
                points.set(points.size() - 1, p);
            }
            else
            {
                points.add(p);
            }
        }
        if (!points.isEmpty())
        {
            Point last = points.get(points.size() - 1);
            if (today > last.day)
            {
                points.add(new Point(today, "", last.value, last.reps, last.weight,
                        last.deduced, 0, true));
            }
        }
        return points;
    }

    // Points dont le jour est >= from (Long.MIN_VALUE = tout).
    public static ArrayList<Point> inPeriod(List<Point> points, long from)
    {
        ArrayList<Point> out = new ArrayList<Point>();
        for (Point p : points)
        {
            if (p.day >= from)
            {
                out.add(p);
            }
        }
        return out;
    }

    // Pour un escalier de records : points de la periode, precedes d'un point technique au
    // debut de la periode qui porte le niveau deja atteint (sinon l'escalier demarrerait
    // en l'air).
    public static ArrayList<Point> stepsInPeriod(List<Point> steps, long from)
    {
        ArrayList<Point> out = new ArrayList<Point>();
        if (from == Long.MIN_VALUE)
        {
            out.addAll(steps);
            return out;
        }
        Point before = null;
        for (Point p : steps)
        {
            if (p.day < from)
            {
                if (!p.extension)
                {
                    before = p;
                }
            }
        }
        boolean firstInPeriodAtFrom = false;
        for (Point p : steps)
        {
            if (p.day >= from && !p.extension)
            {
                firstInPeriodAtFrom = p.day == from;
                break;
            }
        }
        if (before != null && !firstInPeriodAtFrom)
        {
            out.add(new Point(from, "", before.value, before.reps, before.weight,
                    before.deduced, 0, true));
        }
        for (Point p : steps)
        {
            if (p.day >= from)
            {
                out.add(p);
            }
        }
        return out;
    }

    public static ArrayList<Point> realPoints(List<Point> points)
    {
        ArrayList<Point> out = new ArrayList<Point>();
        for (Point p : points)
        {
            if (!p.extension)
            {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ tendance

    // Regression lineaire valeur = a + b * (jour - jour du 1er point). Retourne
    // {pente par jour, ordonnee a l'origine, jour d'origine} ou null s'il y a moins de 2
    // points ou si tous tombent le meme jour.
    public static double[] linearFit(List<Point> points)
    {
        ArrayList<Point> pts = realPoints(points);
        if (pts.size() < 2)
        {
            return null;
        }
        long x0 = pts.get(0).day;
        double n = pts.size();
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (Point p : pts)
        {
            double x = p.day - x0;
            sx += x;
            sy += p.value;
            sxx += x * x;
            sxy += x * p.value;
        }
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-9)
        {
            return null;
        }
        double slope = (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{slope, intercept, x0};
    }

    public static double fitValueAt(double[] fit, long day)
    {
        return fit[1] + fit[0] * (day - (long) fit[2]);
    }

    // ------------------------------------------------------------------ profil par reps

    // Une ligne par nombre de reps de l'historique des PR : record absolu et meilleur poids
    // (pour au moins ce nombre de reps) sur les seances dont le jour est >= from.
    public static ArrayList<ProfileRow> repProfile(List<Session> sessions,
                                                    Map<Integer, ? extends List<RepRangePREvent>> history,
                                                    long from, long today)
    {
        ArrayList<ProfileRow> rows = new ArrayList<ProfileRow>();
        if (history == null)
        {
            return rows;
        }
        TreeMap<Integer, List<RepRangePREvent>> sorted = new TreeMap<Integer, List<RepRangePREvent>>();
        for (Map.Entry<Integer, ? extends List<RepRangePREvent>> e : history.entrySet())
        {
            sorted.put(e.getKey(), e.getValue());
        }
        for (Map.Entry<Integer, List<RepRangePREvent>> e : sorted.entrySet())
        {
            List<RepRangePREvent> events = e.getValue();
            if (events == null || events.isEmpty())
            {
                continue;
            }
            RepRangePREvent last = events.get(events.size() - 1);
            ProfileRow row = new ProfileRow();
            row.reps = e.getKey();
            row.recordWeight = last.getWeight();
            row.recordDate = last.getDate().substring(0, 10);
            row.recordDeduced = last.isDeduced();
            row.recordSourceReps = last.getSourceReps();
            long recordDay = epochDay(row.recordDate);
            row.daysSinceRecord = recordDay < 0 ? 0 : Math.max(0, today - recordDay);

            for (Session session : sessions)
            {
                if (session.day < from)
                {
                    continue;
                }
                for (WorkoutSet s : session.sets)
                {
                    if (roundReps(s) >= row.reps
                            && (Double.isNaN(row.recentWeight) || s.getWeight() > row.recentWeight))
                    {
                        row.recentWeight = s.getWeight();
                        row.recentDate = session.date;
                    }
                }
            }
            if (!Double.isNaN(row.recentWeight) && row.recordWeight > 0)
            {
                row.gapPct = (row.recentWeight / row.recordWeight - 1.0) * 100.0;
            }
            rows.add(row);
        }
        return rows;
    }

    // ------------------------------------------------------------------ textes

    public static String formatKg(double v)
    {
        if (Math.abs(v - Math.rint(v)) < 0.05)
        {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format(Locale.US, "%.1f", v);
    }

    private static String signed(double v)
    {
        String s = formatKg(Math.abs(v));
        if (v > 0.0 && !s.equals("0"))
        {
            return "+" + s;
        }
        if (v < 0.0 && !s.equals("0"))
        {
            return "-" + s;
        }
        return "0";
    }

    private static String signedPct(double pct)
    {
        String s = String.format(Locale.US, "%.1f", Math.abs(pct));
        if (pct > 0.05)
        {
            return "+" + s + " %";
        }
        if (pct < -0.05)
        {
            return "-" + s + " %";
        }
        return "0 %";
    }

    private static String ago(long days)
    {
        if (days <= 0)
        {
            return "aujourd'hui";
        }
        return "il y a " + days + (days == 1 ? " jour" : " jours");
    }

    private static String plural(int n, String one, String many)
    {
        return n + " " + (n > 1 ? many : one);
    }

    // Analyse en clair d'une serie par seance (E1RM ou poids max pour N reps).
    // all = toute la serie de l'exercice (pour le record absolu), period = ses points sur la
    // periode affichee. noun = "1RM estime" ou "poids".
    public static String analyseSeries(List<Point> all, List<Point> period, long today,
                                       String noun, Locale locale)
    {
        ArrayList<Point> allReal = realPoints(all);
        ArrayList<Point> per = realPoints(period);
        StringBuilder sb = new StringBuilder();

        if (per.isEmpty())
        {
            sb.append("Aucune séance sur cette période.");
            if (!allReal.isEmpty())
            {
                Point last = allReal.get(allReal.size() - 1);
                sb.append(" Dernière séance : ").append(ago(today - last.day)).append(".");
            }
            return sb.toString();
        }

        Point last = per.get(per.size() - 1);
        Point best = per.get(0);
        for (Point p : per)
        {
            if (p.value > best.value)
            {
                best = p;
            }
        }

        // Tendance
        double[] fit = linearFit(per);
        long span = last.day - per.get(0).day;
        if (per.size() >= 3 && span >= 14 && fit != null)
        {
            double perMonth = fit[0] * 30.4;
            double mean = 0;
            for (Point p : per)
            {
                mean += p.value;
            }
            mean /= per.size();
            double pct = mean > 0 ? perMonth / mean * 100.0 : 0.0;
            String verdict = pct > 1.0 ? "en progression" : (pct < -1.0 ? "en baisse" : "stable");
            sb.append("Tendance : ").append(signed(perMonth)).append(" kg/mois (")
                    .append(signedPct(pct)).append("), ").append(verdict).append(".\n");
        }
        else
        {
            sb.append("Tendance : pas assez de séances sur la période pour la calculer.\n");
        }

        // Derniere seance vs meilleure de la periode
        sb.append("Dernière séance (").append(formatDay(last.day, false, locale)).append(") : ")
                .append(formatKg(last.value)).append(" kg");
        if (last == best || last.value >= best.value)
        {
            sb.append(", c'est le meilleur ").append(noun).append(" de la période.\n");
        }
        else
        {
            double gap = (last.value / best.value - 1.0) * 100.0;
            sb.append(", ").append(signedPct(gap)).append(" par rapport au meilleur de la période (")
                    .append(formatKg(best.value)).append(" kg, ")
                    .append(formatDay(best.day, false, locale)).append(").\n");
        }

        // Record absolu et nombre de seances depuis
        Point record = allReal.get(0);
        for (Point p : allReal)
        {
            if (p.value > record.value)
            {
                record = p;
            }
        }
        int since = 0;
        for (Point p : allReal)
        {
            if (p.day > record.day)
            {
                since++;
            }
        }
        sb.append("Record absolu : ").append(formatKg(record.value)).append(" kg le ")
                .append(formatDay(record.day, true, locale)).append(" (").append(ago(today - record.day));
        if (since > 0)
        {
            sb.append(", ").append(plural(since, "séance", "séances")).append(" depuis");
        }
        sb.append(").");
        return sb.toString();
    }

    // Analyse en clair de l'escalier des records pour `reps`.
    public static String analyseRecords(List<Point> steps, int reps, long from, long today,
                                        Locale locale)
    {
        ArrayList<Point> real = realPoints(steps);
        if (real.isEmpty())
        {
            return "Aucune série à " + reps + " reps ou plus pour cet exercice.";
        }
        Point cur = real.get(real.size() - 1);
        StringBuilder sb = new StringBuilder();
        sb.append("Record actuel à ").append(reps).append(" reps : ").append(formatKg(cur.value))
                .append(" kg (").append(formatDay(cur.day, true, locale)).append(", ")
                .append(ago(today - cur.day)).append(").\n");

        Point base = null;
        int inPeriod = 0;
        Point firstIn = null;
        for (Point p : real)
        {
            if (p.day < from)
            {
                base = p;
            }
            else
            {
                inPeriod++;
                if (firstIn == null)
                {
                    firstIn = p;
                }
            }
        }
        if (inPeriod == 0)
        {
            sb.append("Aucun record battu sur la période : stagnation depuis ")
                    .append(Math.max(0, today - cur.day)).append(" jours.");
        }
        else
        {
            int broken = inPeriod;
            double reference;
            if (base != null)
            {
                reference = base.value;
            }
            else
            {
                reference = firstIn.value;
                broken = inPeriod - 1;
            }
            double gain = cur.value - reference;
            if (broken <= 0)
            {
                sb.append("Premier record enregistré sur la période.");
            }
            else
            {
                sb.append(plural(broken, "record battu", "records battus")).append(" sur la période (")
                        .append(signed(gain)).append(" kg).");
            }
        }
        return sb.toString();
    }

    // Analyse en clair du profil par reps : ou ca peche.
    public static String analyseProfile(List<ProfileRow> rows, String periodLabel)
    {
        if (rows.isEmpty())
        {
            return "Aucun record enregistré pour cet exercice.";
        }
        // On ne juge que les nombres de reps ou le record vient d'une serie reellement
        // faite a ce nombre de reps : les cases deduites ne sont que la consequence des
        // lignes du dessus.
        ArrayList<ProfileRow> real = new ArrayList<ProfileRow>();
        for (ProfileRow r : rows)
        {
            if (!r.recordDeduced)
            {
                real.add(r);
            }
        }
        if (real.isEmpty())
        {
            real.addAll(rows);
        }

        ArrayList<ProfileRow> lagging = new ArrayList<ProfileRow>();
        ArrayList<ProfileRow> notWorked = new ArrayList<ProfileRow>();
        for (ProfileRow r : real)
        {
            if (!r.worked())
            {
                notWorked.add(r);
            }
            else if (r.gapPct < -WEAK_GAP_PCT)
            {
                lagging.add(r);
            }
        }
        Collections.sort(lagging, new java.util.Comparator<ProfileRow>()
        {
            @Override
            public int compare(ProfileRow a, ProfileRow b)
            {
                return Double.compare(a.gapPct, b.gapPct);
            }
        });

        String scope = periodLabel.equals("all") ? "tout l'historique" : "les " + periodLabel;
        StringBuilder sb = new StringBuilder();
        sb.append("Meilleur sur ").append(scope).append(" comparé au record absolu, par nombre de reps.\n");

        if (lagging.isEmpty() && notWorked.isEmpty())
        {
            sb.append("Aucun point faible visible : tous tes nombres de reps sont à moins de ")
                    .append(formatKg(WEAK_GAP_PCT)).append(" % de leur record.");
            return sb.toString();
        }

        if (!lagging.isEmpty())
        {
            sb.append("En retrait :\n");
            int shown = Math.min(3, lagging.size());
            for (int i = 0; i < shown; i++)
            {
                ProfileRow r = lagging.get(i);
                sb.append("• ").append(r.reps).append(" reps : ").append(formatKg(r.recentWeight))
                        .append(" kg contre ").append(formatKg(r.recordWeight)).append(" kg (")
                        .append(signedPct(r.gapPct)).append("), record ").append(ago(r.daysSinceRecord))
                        .append("\n");
            }
        }
        if (!notWorked.isEmpty())
        {
            sb.append("Pas travaillé sur la période : ");
            int shown = Math.min(6, notWorked.size());
            for (int i = 0; i < shown; i++)
            {
                if (i > 0)
                {
                    sb.append(", ");
                }
                sb.append(notWorked.get(i).reps);
            }
            if (notWorked.size() > shown)
            {
                sb.append("...");
            }
            sb.append(" reps.");
        }
        return sb.toString().trim();
    }
}
