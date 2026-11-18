package com.example.verifit.ui;

import com.example.verifit.EngineSession;

import java.security.SecureRandom;

/**
 * Plan genere dans l'app (lot M4.2), en attente dans l'apercu (ImportSessionActivity).
 *
 * L'apercu est un ecran ouvert aux autres applis (« Ouvrir avec FitEngine ») : le plan ne lui
 * est donc PAS passe dans l'Intent (une autre appli pourrait en fabriquer un), mais garde ici,
 * en memoire du process, avec un jeton aleatoire. L'Intent ne porte que le jeton ; l'apercu
 * n'accepte le plan que si le jeton correspond. Un seul plan a la fois.
 */
final class GeneratedPlan
{
    static final String EXTRA_TOKEN = "generated_plan_token";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static GeneratedPlan current;

    final String token;
    final String fileName;
    final EngineSession.Result result;

    private GeneratedPlan(String token, String fileName, EngineSession.Result result)
    {
        this.token = token;
        this.fileName = fileName;
        this.result = result;
    }

    static synchronized GeneratedPlan put(String fileName, EngineSession.Result result)
    {
        current = new GeneratedPlan(Long.toHexString(RANDOM.nextLong()) + Long.toHexString(RANDOM.nextLong()),
                fileName, result);
        return current;
    }

    // Plan correspondant au jeton, ou null (jeton absent, faux, ou process redemarre).
    static synchronized GeneratedPlan find(String token)
    {
        return token != null && current != null && current.token.equals(token) ? current : null;
    }

    static synchronized void clear(String token)
    {
        if (current != null && current.token.equals(token))
        {
            current = null;
        }
    }
}
