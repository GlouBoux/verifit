package com.example.verifit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.verifit.ExerciseGraphData.Point;
import com.example.verifit.ExerciseGraphData.ProfileRow;
import com.example.verifit.ExerciseGraphData.Session;
import com.example.verifit.model.WorkoutSet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

// Tests des calculs de l'onglet Graph (ExerciseGraphData), sans Android.
public class ExerciseGraphDataTest
{
    private static final String EX = "Squat";

    private static WorkoutSet set(String date, double reps, double weight)
    {
        return new WorkoutSet(date, EX, "Legs", reps, weight);
    }

    // Reproduit DataStorage.calculateRepRangeHistory() (transitivite) pour construire un
    // historique de PR coherent avec l'app sans dependre de DataStorage.
    private static TreeMap<Integer, ArrayList<RepRangePREvent>> history(List<WorkoutSet> sets)
    {
        ArrayList<WorkoutSet> sorted = new ArrayList<WorkoutSet>(sets);
        Collections.sort(sorted, new Comparator<WorkoutSet>()
        {
            @Override
            public int compare(WorkoutSet a, WorkoutSet b)
            {
                return a.getDate().compareTo(b.getDate());
            }
        });
        TreeMap<Integer, ArrayList<RepRangePREvent>> history = new TreeMap<Integer, ArrayList<RepRangePREvent>>();
        HashMap<Integer, Double> best = new HashMap<Integer, Double>();
        for (WorkoutSet s : sorted)
        {
            if (s.getReps() == 0.0)
            {
                continue;
            }
            int setReps = (int) Math.round(s.getReps());
            for (int r = 1; r <= setReps; r++)
            {
                Double prev = best.get(r);
                if (prev == null || s.getWeight() > prev)
                {
                    best.put(r, s.getWeight());
                    if (!history.containsKey(r))
                    {
                        history.put(r, new ArrayList<RepRangePREvent>());
                    }
                    history.get(r).add(new RepRangePREvent(s.getWeight(), s.getDate(), setReps, setReps != r, s));
                }
            }
        }
        return history;
    }

    private static ArrayList<WorkoutSet> sample()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-08-01", 8, 40));
        sets.add(set("2026-08-01", 5, 50));
        sets.add(set("2026-08-15", 8, 42.5));
        sets.add(set("2026-09-01", 10, 40));
        sets.add(set("2026-09-01", 8, 42.5));
        sets.add(set("2026-09-20", 8, 45));
        sets.add(set("2026-09-20", 3, 60));
        return sets;
    }

    // ---- dates

    @Test
    public void epochDay_connuesEtComparables()
    {
        assertEquals(0L, ExerciseGraphData.epochDay("1970-01-01"));
        assertEquals(1L, ExerciseGraphData.epochDay("1970-01-02"));
        assertEquals(19723L, ExerciseGraphData.epochDay("2024-01-01"));
        // 2024 est bissextile : 29 fevrier existe
        assertEquals(1L, ExerciseGraphData.epochDay("2024-03-01") - ExerciseGraphData.epochDay("2024-02-29"));
        assertEquals(7L, ExerciseGraphData.epochDay("2026-09-27") - ExerciseGraphData.epochDay("2026-09-20"));
        // Format avec heure accepte
        assertEquals(ExerciseGraphData.epochDay("2026-09-20"), ExerciseGraphData.epochDay("2026-09-20 10:00:00"));
    }

    @Test
    public void epochDay_illisible()
    {
        assertEquals(-1L, ExerciseGraphData.epochDay(null));
        assertEquals(-1L, ExerciseGraphData.epochDay(""));
        assertEquals(-1L, ExerciseGraphData.epochDay("20/09/2026"));
        assertEquals(-1L, ExerciseGraphData.epochDay("2026-13-01"));
    }

    @Test
    public void formatDay_francais()
    {
        long d = ExerciseGraphData.epochDay("2026-09-07");
        assertEquals("7 sept.", ExerciseGraphData.formatDay(d, false, Locale.FRANCE));
        assertEquals("7 sept. 2026", ExerciseGraphData.formatDay(d, true, Locale.FRANCE));
    }

    // ---- seances

    @Test
    public void buildSessions_groupeParDateEtEcarteLesSeriesInutilisables()
    {
        ArrayList<WorkoutSet> sets = sample();
        sets.add(set("2026-09-25", 0, 40));      // 0 rep
        sets.add(set("2026-09-25", 10, 0));      // 0 kg
        sets.add(set("pas une date", 8, 40));    // date illisible
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sets);
        assertEquals(4, sessions.size());
        assertEquals("2026-08-01", sessions.get(0).date);
        assertEquals(2, sessions.get(0).sets.size());
        assertEquals("2026-09-20", sessions.get(3).date);
    }

    @Test
    public void buildSessions_ordreChronologiqueMemeSiDesordre()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-09-20", 8, 45));
        sets.add(set("2026-08-01", 8, 40));
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sets);
        assertEquals("2026-08-01", sessions.get(0).date);
    }

    @Test
    public void performedRepCounts_etDefaut()
    {
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sample());
        List<Integer> counts = ExerciseGraphData.performedRepCounts(sessions);
        assertEquals(java.util.Arrays.asList(3, 5, 8, 10), counts);
        assertEquals(8, ExerciseGraphData.defaultReps(sessions));
        assertEquals(-1, ExerciseGraphData.defaultReps(new ArrayList<Session>()));
    }

    // ---- e1RM

    @Test
    public void e1rm_meilleureSerieParSeance()
    {
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sample());
        ArrayList<Point> pts = ExerciseGraphData.e1rmSeries(sessions);
        assertEquals(4, pts.size());
        // 2026-08-01 : 8x40 -> 40*(1+8/30)=50.667 ; 5x50 -> 58.333 (meilleure)
        assertEquals(58.333, pts.get(0).value, 0.01);
        assertEquals(5, pts.get(0).reps);
        assertEquals(50.0, pts.get(0).weight, 1e-9);
        assertEquals(2, pts.get(0).setCount);
        // 2026-09-20 : 3x60 -> 66 (meilleure)
        assertEquals(66.0, pts.get(3).value, 0.01);
        // Records : le 1er n'en est pas un ; 15/08 = 42.5*(1+8/30)=53.83 < 58.33 -> non ;
        // 01/09 : 10x40=53.33, 8x42.5=53.83 -> non ; 20/09 : 66 > 58.33 -> record
        assertFalse(pts.get(0).record);
        assertFalse(pts.get(1).record);
        assertFalse(pts.get(2).record);
        assertTrue(pts.get(3).record);
    }

    // ---- max weight for reps

    @Test
    public void maxWeightForReps_transitiviteEtDeduit()
    {
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sample());
        ArrayList<Point> pts = ExerciseGraphData.maxWeightForReps(sessions, 8);
        // 8 reps ou plus : 01/08 -> 40 ; 15/08 -> 42.5 ; 01/09 -> 42.5 (10x40 et 8x42.5) ;
        // 20/09 -> 45 (3x60 ne compte pas)
        assertEquals(4, pts.size());
        assertEquals(40.0, pts.get(0).value, 1e-9);
        assertEquals(42.5, pts.get(1).value, 1e-9);
        assertEquals(42.5, pts.get(2).value, 1e-9);
        assertEquals(45.0, pts.get(3).value, 1e-9);
        assertFalse(pts.get(2).deduced);
        assertTrue(pts.get(1).record);
        assertFalse(pts.get(2).record);
        assertTrue(pts.get(3).record);
    }

    @Test
    public void maxWeightForReps_serieDeduiteQuandPlusDeReps()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-09-01", 10, 40));
        ArrayList<Point> pts = ExerciseGraphData.maxWeightForReps(ExerciseGraphData.buildSessions(sets), 8);
        assertEquals(1, pts.size());
        assertTrue(pts.get(0).deduced);
        assertEquals(10, pts.get(0).reps);
    }

    @Test
    public void maxWeightForReps_egaliteDePoidsPrefereLaSerieExacte()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-09-01", 12, 40));
        sets.add(set("2026-09-01", 8, 40));
        ArrayList<Point> pts = ExerciseGraphData.maxWeightForReps(ExerciseGraphData.buildSessions(sets), 8);
        assertEquals(8, pts.get(0).reps);
        assertFalse(pts.get(0).deduced);
    }

    @Test
    public void maxWeightForReps_aucuneSerieAssezLongue()
    {
        ArrayList<Point> pts = ExerciseGraphData.maxWeightForReps(ExerciseGraphData.buildSessions(sample()), 30);
        assertTrue(pts.isEmpty());
    }

    // ---- escalier de records

    @Test
    public void recordSteps_escalierEtProlongement()
    {
        ArrayList<WorkoutSet> sets = sample();
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sets);
        long today = ExerciseGraphData.epochDay("2026-10-06");
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, today);
        // 8 reps : 01/08 40 ; 15/08 42.5 ; 20/09 45 ; (01/09 : 10x40 non, 8x42.5 non) + prolongement
        assertEquals(4, steps.size());
        assertEquals(40.0, steps.get(0).value, 1e-9);
        assertEquals(42.5, steps.get(1).value, 1e-9);
        assertEquals(45.0, steps.get(2).value, 1e-9);
        assertTrue(steps.get(3).extension);
        assertEquals(45.0, steps.get(3).value, 1e-9);
        assertEquals(today, steps.get(3).day);
        for (int i = 1; i < steps.size(); i++)
        {
            assertTrue(steps.get(i).value >= steps.get(i - 1).value);
        }
    }

    @Test
    public void recordSteps_pasDeProlongementSiRecordDuJour()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sample());
        long today = ExerciseGraphData.epochDay("2026-09-20");
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, today);
        assertFalse(steps.get(steps.size() - 1).extension);
    }

    @Test
    public void recordSteps_pasDHistorique()
    {
        assertTrue(ExerciseGraphData.recordSteps(new TreeMap<Integer, ArrayList<RepRangePREvent>>(), 8, 20000).isEmpty());
        assertTrue(ExerciseGraphData.recordSteps(null, 8, 20000).isEmpty());
    }

    @Test
    public void recordSteps_memeJourGardeLePlusHaut()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-09-01", 5, 40));
        sets.add(set("2026-09-01", 5, 45));
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(history(sets), 5, ExerciseGraphData.epochDay("2026-09-01"));
        assertEquals(1, steps.size());
        assertEquals(45.0, steps.get(0).value, 1e-9);
    }

    @Test
    public void stepsInPeriod_porteLeNiveauAcquisAuDebut()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sample());
        long today = ExerciseGraphData.epochDay("2026-10-06");
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, today);
        long from = ExerciseGraphData.epochDay("2026-09-01");
        ArrayList<Point> inPeriod = ExerciseGraphData.stepsInPeriod(steps, from);
        // point technique a "from" a 42.5 (niveau du 15/08), puis le record du 20/09, puis le prolongement
        assertEquals(3, inPeriod.size());
        assertTrue(inPeriod.get(0).extension);
        assertEquals(from, inPeriod.get(0).day);
        assertEquals(42.5, inPeriod.get(0).value, 1e-9);
        assertEquals(45.0, inPeriod.get(1).value, 1e-9);
    }

    @Test
    public void stepsInPeriod_aucunRecordDansLaPeriodeDonneUneLignePlate()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sample());
        long today = ExerciseGraphData.epochDay("2026-10-06");
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, today);
        ArrayList<Point> inPeriod = ExerciseGraphData.stepsInPeriod(steps, ExerciseGraphData.epochDay("2026-09-25"));
        assertEquals(2, inPeriod.size());
        assertTrue(inPeriod.get(0).extension);
        assertTrue(inPeriod.get(1).extension);
        assertEquals(45.0, inPeriod.get(0).value, 1e-9);
        assertTrue(ExerciseGraphData.realPoints(inPeriod).isEmpty());
    }

    @Test
    public void stepsInPeriod_toutLHistorique()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sample());
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, ExerciseGraphData.epochDay("2026-10-06"));
        assertEquals(steps.size(), ExerciseGraphData.stepsInPeriod(steps, Long.MIN_VALUE).size());
    }

    // ---- tendance

    @Test
    public void linearFit_droiteParfaite()
    {
        ArrayList<Point> pts = new ArrayList<Point>();
        long d0 = ExerciseGraphData.epochDay("2026-09-01");
        for (int i = 0; i < 5; i++)
        {
            pts.add(new Point(d0 + i * 10, "x", 100 + 2.0 * i * 10 / 10.0, 5, 50, false, 1, false));
        }
        double[] fit = ExerciseGraphData.linearFit(pts);
        assertNotNull(fit);
        assertEquals(0.2, fit[0], 1e-9);
        assertEquals(100.0, ExerciseGraphData.fitValueAt(fit, d0), 1e-9);
        assertEquals(108.0, ExerciseGraphData.fitValueAt(fit, d0 + 40), 1e-9);
    }

    @Test
    public void linearFit_pasAssezDePoints()
    {
        ArrayList<Point> pts = new ArrayList<Point>();
        assertNull(ExerciseGraphData.linearFit(pts));
        pts.add(new Point(20000, "x", 100, 5, 50, false, 1, false));
        assertNull(ExerciseGraphData.linearFit(pts));
        pts.add(new Point(20000, "x", 110, 5, 50, false, 1, false));
        assertNull(ExerciseGraphData.linearFit(pts));
    }

    @Test
    public void linearFit_ignoreLesPointsTechniques()
    {
        ArrayList<Point> pts = new ArrayList<Point>();
        pts.add(new Point(20000, "x", 100, 5, 50, false, 1, false));
        pts.add(new Point(20010, "x", 110, 5, 50, false, 1, false));
        pts.add(new Point(20500, "", 999, 5, 50, false, 0, true));
        double[] fit = ExerciseGraphData.linearFit(pts);
        assertEquals(1.0, fit[0], 1e-9);
    }

    // ---- profil par reps

    @Test
    public void repProfile_recordContreMeilleurRecent()
    {
        ArrayList<WorkoutSet> sets = sample();
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sets);
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sets);
        long today = ExerciseGraphData.epochDay("2026-10-06");
        long from = ExerciseGraphData.epochDay("2026-09-10");   // seule la seance du 20/09
        ArrayList<ProfileRow> rows = ExerciseGraphData.repProfile(sessions, h, from, today);
        // reps 1..10 presentes dans l'historique
        assertEquals(10, rows.size());
        ProfileRow r8 = rows.get(7);
        assertEquals(8, r8.reps);
        assertEquals(45.0, r8.recordWeight, 1e-9);
        assertEquals("2026-09-20", r8.recordDate);
        assertFalse(r8.recordDeduced);
        assertEquals(45.0, r8.recentWeight, 1e-9);
        assertEquals(0.0, r8.gapPct, 1e-9);
        assertEquals(16L, r8.daysSinceRecord);

        // 10 reps : record 40 (01/09, serie de 10) ; rien de ce niveau depuis le 10/09
        ProfileRow r10 = rows.get(9);
        assertEquals(40.0, r10.recordWeight, 1e-9);
        assertFalse(r10.worked());

        // 3 reps : record 60 le 20/09 (reel), recent 60
        ProfileRow r3 = rows.get(2);
        assertEquals(60.0, r3.recordWeight, 1e-9);
        assertEquals(60.0, r3.recentWeight, 1e-9);

        // 5 reps : record 50 (01/08, reel) ; meilleur recent pour >= 5 reps depuis le 10/09 = 45
        ProfileRow r5 = rows.get(4);
        assertEquals(50.0, r5.recordWeight, 1e-9);
        assertEquals(45.0, r5.recentWeight, 1e-9);
        assertEquals(-10.0, r5.gapPct, 1e-9);
    }

    @Test
    public void repProfile_historiqueVide()
    {
        assertTrue(ExerciseGraphData.repProfile(new ArrayList<Session>(), null, Long.MIN_VALUE, 20000).isEmpty());
    }

    // ---- textes d'analyse

    @Test
    public void analyseSeries_progression()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-08-01", 8, 40));
        sets.add(set("2026-08-15", 8, 42.5));
        sets.add(set("2026-09-01", 8, 45));
        sets.add(set("2026-09-15", 8, 47.5));
        sets.add(set("2026-10-01", 8, 50));
        ArrayList<Point> all = ExerciseGraphData.maxWeightForReps(ExerciseGraphData.buildSessions(sets), 8);
        long today = ExerciseGraphData.epochDay("2026-10-06");
        String txt = ExerciseGraphData.analyseSeries(all, all, today, "poids", Locale.FRANCE);
        assertTrue(txt, txt.contains("en progression"));
        assertTrue(txt, txt.contains("c'est le meilleur poids de la période"));
        assertTrue(txt, txt.contains("Record absolu : 50 kg le 1 oct. 2026 (il y a 5 jours)"));
    }

    @Test
    public void analyseSeries_baisseEtRecordAncien()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-08-01", 8, 50));
        sets.add(set("2026-08-20", 8, 47.5));
        sets.add(set("2026-09-10", 8, 45));
        sets.add(set("2026-09-30", 8, 42.5));
        ArrayList<Point> all = ExerciseGraphData.maxWeightForReps(ExerciseGraphData.buildSessions(sets), 8);
        long today = ExerciseGraphData.epochDay("2026-10-06");
        String txt = ExerciseGraphData.analyseSeries(all, all, today, "poids", Locale.FRANCE);
        assertTrue(txt, txt.contains("en baisse"));
        assertTrue(txt, txt.contains("-15.0 %"));
        assertTrue(txt, txt.contains("3 séances depuis"));
    }

    @Test
    public void analyseSeries_peuDeSeances()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-09-30", 8, 42.5));
        sets.add(set("2026-10-02", 8, 45));
        ArrayList<Point> all = ExerciseGraphData.maxWeightForReps(ExerciseGraphData.buildSessions(sets), 8);
        String txt = ExerciseGraphData.analyseSeries(all, all, ExerciseGraphData.epochDay("2026-10-06"), "poids", Locale.FRANCE);
        assertTrue(txt, txt.contains("pas assez de séances"));
    }

    @Test
    public void analyseSeries_periodeVide()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-06-01", 8, 42.5));
        ArrayList<Point> all = ExerciseGraphData.maxWeightForReps(ExerciseGraphData.buildSessions(sets), 8);
        long today = ExerciseGraphData.epochDay("2026-10-06");
        String txt = ExerciseGraphData.analyseSeries(all, new ArrayList<Point>(), today, "poids", Locale.FRANCE);
        assertTrue(txt, txt.startsWith("Aucune séance sur cette période."));
        assertTrue(txt, txt.contains("il y a 127 jours"));
    }

    @Test
    public void analyseRecords_stagnation()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sample());
        long today = ExerciseGraphData.epochDay("2026-10-06");
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, today);
        long from = ExerciseGraphData.epochDay("2026-09-25");
        String txt = ExerciseGraphData.analyseRecords(steps, 8, from, today, Locale.FRANCE);
        assertTrue(txt, txt.contains("Record actuel à 8 reps : 45 kg"));
        assertTrue(txt, txt.contains("Aucun record battu sur la période : stagnation depuis 16 jours"));
    }

    @Test
    public void analyseRecords_recordsBattusSurLaPeriode()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sample());
        long today = ExerciseGraphData.epochDay("2026-10-06");
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, today);
        long from = ExerciseGraphData.epochDay("2026-08-10");
        String txt = ExerciseGraphData.analyseRecords(steps, 8, from, today, Locale.FRANCE);
        // niveau avant la periode : 40 ; records dans la periode : 42.5 puis 45 -> 2 records, +5
        assertTrue(txt, txt.contains("2 records battus sur la période (+5 kg)"));
    }

    @Test
    public void analyseRecords_toutHistoriqueSansReference()
    {
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sample());
        long today = ExerciseGraphData.epochDay("2026-10-06");
        ArrayList<Point> steps = ExerciseGraphData.recordSteps(h, 8, today);
        String txt = ExerciseGraphData.analyseRecords(steps, 8, Long.MIN_VALUE, today, Locale.FRANCE);
        // 3 evenements, le premier sert de reference : 2 records battus, +5
        assertTrue(txt, txt.contains("2 records battus sur la période (+5 kg)"));
    }

    @Test
    public void analyseRecords_aucuneSerie()
    {
        String txt = ExerciseGraphData.analyseRecords(new ArrayList<Point>(), 30, Long.MIN_VALUE, 20000, Locale.FRANCE);
        assertTrue(txt, txt.contains("Aucune série à 30 reps"));
    }

    @Test
    public void analyseProfile_pointsFaibles()
    {
        ArrayList<WorkoutSet> sets = sample();
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sets);
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sets);
        long today = ExerciseGraphData.epochDay("2026-10-06");
        long from = ExerciseGraphData.epochDay("2026-09-10");
        ArrayList<ProfileRow> rows = ExerciseGraphData.repProfile(sessions, h, from, today);
        String txt = ExerciseGraphData.analyseProfile(rows, "3m");
        // 5 reps : 45 contre 50 (-10 %) ; 10 reps : non travaille
        assertTrue(txt, txt.contains("5 reps : 45 kg contre 50 kg (-10.0 %)"));
        assertTrue(txt, txt.contains("Pas travaillé sur la période : 10 reps"));
        assertFalse(txt, txt.contains("8 reps : 45 kg contre"));
    }

    @Test
    public void analyseProfile_toutVaBien()
    {
        ArrayList<WorkoutSet> sets = new ArrayList<WorkoutSet>();
        sets.add(set("2026-09-20", 8, 45));
        ArrayList<Session> sessions = ExerciseGraphData.buildSessions(sets);
        TreeMap<Integer, ArrayList<RepRangePREvent>> h = history(sets);
        ArrayList<ProfileRow> rows = ExerciseGraphData.repProfile(sessions, h, Long.MIN_VALUE, ExerciseGraphData.epochDay("2026-10-06"));
        String txt = ExerciseGraphData.analyseProfile(rows, "all");
        assertTrue(txt, txt.contains("Aucun point faible visible"));
    }

    @Test
    public void formatKg()
    {
        assertEquals("45", ExerciseGraphData.formatKg(45.0));
        assertEquals("47.5", ExerciseGraphData.formatKg(47.5));
        assertEquals("47.3", ExerciseGraphData.formatKg(47.26));
        assertEquals("58.3", ExerciseGraphData.formatKg(58.333));
    }
}
