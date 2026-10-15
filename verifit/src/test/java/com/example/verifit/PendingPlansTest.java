package com.example.verifit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Bouton "Importer la seance en attente" (15/10/2026) : choix du plan le plus recent de
 * chaque type dans Documents/FitEngine_plans (PendingPlans, logique pure). Les noms
 * suivent generate_workout.py : Seance_<Type>_<Cycle>_<AAAA-MM-JJ_HH-MM-SS>.json.
 */
public class PendingPlansTest
{
    private static PendingPlans.FileEntry<String> file(String name, long lastModified)
    {
        return new PendingPlans.FileEntry<String>("uri:" + name, name, lastModified, false);
    }

    private static PendingPlans.FileEntry<String> dir(String name)
    {
        return new PendingPlans.FileEntry<String>("uri:" + name, name, 0L, true);
    }

    @SafeVarargs
    private static List<PendingPlans.FileEntry<String>> list(PendingPlans.FileEntry<String>... entries)
    {
        return new ArrayList<PendingPlans.FileEntry<String>>(Arrays.asList(entries));
    }

    @Test
    public void realFileNameIsRecognised()
    {
        PendingPlans.Plan<String> plan = PendingPlans.parse(file("Seance_Force_CycleA_2026-10-15_19-39-29.json", 5L));
        assertEquals("Force", plan.type);
        assertEquals("CycleA", plan.cycle);
        assertEquals("2026-10-15_19-39-29", plan.generatedAt);
        assertEquals("uri:Seance_Force_CycleA_2026-10-15_19-39-29.json", plan.handle);
        assertEquals("15/10/2026 19:39", plan.generatedAtLabel());
    }

    @Test
    public void namesThatAreNotPlansAreRejected()
    {
        assertNull(PendingPlans.parse(file("Seance_Force_CycleA_2026-10-15_19-39-29.md", 1L)));
        assertNull(PendingPlans.parse(file("Seance_Force_CycleA_2026-10-15.json", 1L)));
        assertNull(PendingPlans.parse(file("Seance_Force_CycleA_2026-10-15_19-39-29 (1).json", 1L)));
        assertNull(PendingPlans.parse(file("Seance_Force_CycleA_2026-10-15_19-39-29.sync-conflict-20261015-200000-ABCDEFG.json", 1L)));
        assertNull(PendingPlans.parse(file("fitengine_coaching_latest.json", 1L)));
        assertNull(PendingPlans.parse(dir("Seance_Force_CycleA_2026-10-15_19-39-29.json")));
        assertNull(PendingPlans.parse(null));
    }

    @Test
    public void emptyFolderGivesNothing()
    {
        PendingPlans.Selection<String> selection = PendingPlans.select(list());
        assertTrue(selection.isEmpty());
        assertEquals(0, selection.ignoredFiles);
        assertEquals(0, selection.olderPlans);
    }

    @Test
    public void mostRecentPlanOfEachTypeIsKept()
    {
        PendingPlans.Selection<String> selection = PendingPlans.select(list(
                file("Seance_Force_CycleA_2026-10-12_18-00-00.json", 100L),
                file("Seance_Volume_CycleB_2026-10-14_09-30-00.json", 200L),
                file("Seance_Force_CycleB_2026-10-15_19-39-29.json", 300L),
                file("Seance_Volume_CycleA_2026-10-10_09-30-00.json", 400L)));

        assertEquals(2, selection.latestPerType.size());
        // Du plus recent au plus ancien.
        assertEquals("Seance_Force_CycleB_2026-10-15_19-39-29.json", selection.latestPerType.get(0).fileName);
        assertEquals("Seance_Volume_CycleB_2026-10-14_09-30-00.json", selection.latestPerType.get(1).fileName);
        assertEquals(2, selection.olderPlans);
        assertEquals(0, selection.ignoredFiles);
    }

    @Test
    public void timestampInTheNameWinsOverFileModificationDate()
    {
        // Syncthing peut livrer l'ancien plan apres le nouveau : seule l'heure de
        // generation (dans le nom) compte.
        PendingPlans.Selection<String> selection = PendingPlans.select(list(
                file("Seance_Force_CycleA_2026-10-15_19-39-29.json", 1000L),
                file("Seance_Force_CycleA_2026-10-14_08-00-00.json", 9000L)));

        assertEquals(1, selection.latestPerType.size());
        assertEquals("2026-10-15_19-39-29", selection.latestPerType.get(0).generatedAt);
        assertEquals(1, selection.olderPlans);
    }

    @Test
    public void sameTimestampFallsBackToModificationDate()
    {
        PendingPlans.Selection<String> selection = PendingPlans.select(list(
                file("Seance_Force_CycleA_2026-10-15_19-39-29.json", 1000L),
                file("Seance_Force_CycleB_2026-10-15_19-39-29.json", 2000L)));

        assertEquals("Seance_Force_CycleB_2026-10-15_19-39-29.json", selection.latestPerType.get(0).fileName);
    }

    @Test
    public void foldersHiddenAndTemporaryFilesAreSkippedSilently()
    {
        PendingPlans.Selection<String> selection = PendingPlans.select(list(
                dir(".stfolder"),
                dir("archives"),
                file(".syncthing.Seance_Force_CycleA_2026-10-16_07-00-00.json.tmp", 1L),
                file("~syncthing~Seance_Force_CycleA_2026-10-16_07-00-00.json.tmp", 1L),
                file(".Seance_Force_CycleA_2026-10-16_07-00-00.json", 1L),
                file("Seance_Force_CycleA_2026-10-15_19-39-29.json", 1L)));

        assertEquals(1, selection.latestPerType.size());
        assertEquals("2026-10-15_19-39-29", selection.latestPerType.get(0).generatedAt);
        assertEquals(0, selection.ignoredFiles);
    }

    @Test
    public void unrecognisedFilesAreCounted()
    {
        PendingPlans.Selection<String> selection = PendingPlans.select(list(
                file("notes.txt", 1L),
                file("Seance_Force_CycleA_2026-10-15_19-39-29 (1).json", 1L),
                file("Seance_Volume_CycleA_2026-10-15_19-39-29.json", 1L)));

        assertEquals(1, selection.latestPerType.size());
        assertEquals("Volume", selection.latestPerType.get(0).type);
        assertEquals(2, selection.ignoredFiles);
    }

    @Test
    public void onlyUnrecognisedFilesGivesAnEmptySelection()
    {
        PendingPlans.Selection<String> selection = PendingPlans.select(list(file("notes.txt", 1L)));
        assertTrue(selection.isEmpty());
        assertEquals(1, selection.ignoredFiles);
    }

    @Test
    public void nullEntriesAreSkipped()
    {
        List<PendingPlans.FileEntry<String>> entries = list(file("Seance_Force_CycleA_2026-10-15_19-39-29.json", 1L));
        entries.add(null);
        assertEquals(1, PendingPlans.select(entries).latestPerType.size());
    }
}
