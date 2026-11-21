package com.example.verifit;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

// Lot M5b.2 : texte de l'ecran de bilan, sur la sortie reelle du moteur (fixture).
public class EngineEndOfSessionViewTest
{
    @Test
    public void poidsEtDates()
    {
        assertEquals("60 kg", EngineEndOfSessionView.kg(60.0));
        assertEquals("52,5 kg", EngineEndOfSessionView.kg(52.5));
        assertEquals("52,25 kg", EngineEndOfSessionView.kg(52.25));
        assertEquals("20/11", EngineEndOfSessionView.shortDate("2026-11-20", "2026"));
        assertEquals("03/01/2025", EngineEndOfSessionView.shortDate("2025-01-03", "2026"));
        assertEquals("Force, cycle A (plan du 19/11)", EngineEndOfSessionView.planTitle("Seance_Force_CycleA_2026-11-19_10-00-00"));
        assertEquals("autre_nom", EngineEndOfSessionView.planTitle("autre_nom"));
    }

    @Test
    public void bilan_prObjectifsHorsPlanEtValider() throws IOException
    {
        EngineEndOfSession.Result r = EngineEndOfSession.parse(EngineEndOfSessionTest.fixture());
        String text = EngineEndOfSessionView.text(r, "2026-11-20T19:30:00");
        assertEquals("Bilan de fin de séance", EngineEndOfSessionView.title(r));
        assertTrue(EngineEndOfSessionView.text(r, "2027-01-02T10:00:00").contains("  RM 8 : 85 kg → 90 kg (90 × 8 le 20/11/2026)\n"));
        assertTrue(text, text.contains("NOUVEAUX PR PROPOSÉS (8 RM, 1 exercice)"));
        assertTrue(text, text.contains("  RM 8 : 85 kg → 90 kg (90 × 8 le 20/11)\n"));
        assertTrue(text, text.contains("  RM 7 : vide → 90 kg (90 × 8 le 20/11, cascade)\n"));
        assertTrue(text, text.contains("  RM 9 : vide → 78 kg (case vide remplie depuis les RM au-dessus)\n"));
        assertTrue(text, text.contains("FORCE, CYCLE A (PLAN DU 19/11)\n  Objectifs atteints : 1/2\n"));
        assertTrue(text, text.contains("  ✓ Squat S1 : 90 kg × 8\n"));
        assertTrue(text, text.contains("  ✗ Squat S2 : 80 kg × 10, tenu 7 (échec)\n"));
        assertTrue(text, text.contains("  + Hors plan : Squat 60 kg × 15, record\n"));
        assertTrue(text, text.contains("  8 PR écrits"));
        assertTrue(text, text.contains("  3 ligne(s) ajoutée(s) au journal des séries\n"));
        assertTrue(text, text.endsWith("Rien n'est écrit si tu fermes."));
    }

    @Test
    public void bilan_dejaValide()
    {
        String raw = "{\"version\": 1, \"pr\": [], \"rapport\": {\"sans_changement\": [], \"sans_serie\": [], \"sans_tableau\": []},"
                + " \"donnees_apres\": null, \"journal_only\": true, \"plans\": [], \"remplaces\": [], \"plans_a_retirer\": [],"
                + " \"journal\": [], \"lignes_deja_ecrites\": 5, \"historique_pr\": [], \"resume\": {\"status\": \"no_plan\"},"
                + " \"calcul_ms\": 3.0}";
        EngineEndOfSession.Result r = EngineEndOfSession.parse(raw);
        assertEquals("Bilan : rien de nouveau", EngineEndOfSessionView.title(r));
        String text = EngineEndOfSessionView.text(r, "2026-11-21T15:00:00");
        assertTrue(text, text.contains("Aucun nouveau PR."));
        assertTrue(text, text.contains("Aucun plan en attente"));
        assertTrue(text, text.endsWith("ce bilan a déjà été validé."));
    }
}
