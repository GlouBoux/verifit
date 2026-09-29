package com.example.verifit;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;

import com.example.verifit.ui.MainActivity;
import com.example.verifit.verifitrs.ResponseLoginUser;
import com.google.gson.Gson;

public class SharedPreferences {

    Context context;

    public SharedPreferences(Context context)
    {
        this.context = context;
    }

    public void save(String value, String key)
    {
        android.content.SharedPreferences sharedPreferences = context.getSharedPreferences("shared preferences", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    public String load(String key)
    {
        android.content.SharedPreferences sharedPreferences = context.getSharedPreferences("shared preferences", MODE_PRIVATE);
        String text = sharedPreferences.getString(key, "");
        return text;
    }

    public void enableOfflineMode()
    {
        SharedPreferences sharedPreferences = new SharedPreferences(context);
        sharedPreferences.save("", "verifit_rs_token");
        sharedPreferences.save("offline","mode");
        // Plus d'effacement des donnees ici (29/09/2026) : cette methode est appelee par
        // le bouton "Cancel" de l'ecran Login et par tout echec de connexion au backend
        // verifit_rs (abandonne) - un simple tap Reglages > Login/Sign Up > Cancel
        // effacait donc tout l'historique local. Le backend entier part au lot B de la
        // revue d'architecture du 28/09/2026.
    }

    public void enableOnlineMode(String responseBody, String username, String password)
    {
        Gson gson = new Gson();
        ResponseLoginUser responseUser = gson.fromJson(responseBody, ResponseLoginUser.class);
        SharedPreferences sharedPreferences = new SharedPreferences(context);
        sharedPreferences.save(responseUser.getToken(), "verifit_rs_token");
        sharedPreferences.save(username, "verifit_rs_username");
        sharedPreferences.save("online","mode");
    }

    public void saveUsername(String username)
    {
        SharedPreferences sharedPreferences = new SharedPreferences(context);
        sharedPreferences.save(username, "verifit_rs_username");
        sharedPreferences.save("online","mode");
    }

    public boolean isOfflineMode()
    {
        SharedPreferences sharedPreferences = new SharedPreferences(context);
        return sharedPreferences.load("mode").equals("offline") || sharedPreferences.load("mode").equals("");
    }

    public void disableCaching(){
        SharedPreferences sharedPreferences = new SharedPreferences(context);
        sharedPreferences.save("true", "refresh_required");
    }

    public void enableCaching(){
        SharedPreferences sharedPreferences = new SharedPreferences(context);
        sharedPreferences.save("", "refresh_required");
    }

    public boolean shouldUseCache() {
        SharedPreferences sharedPreferences = new SharedPreferences(context);
        String should_cache = sharedPreferences.load("refresh_required");
        if (should_cache.isEmpty()) {
            return true;
        }
        return false;
    }
}
