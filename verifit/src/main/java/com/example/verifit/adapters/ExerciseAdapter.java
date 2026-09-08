package com.example.verifit.adapters;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.LoadingDialog;
import com.example.verifit.SharedPreferences;
import com.example.verifit.SnackBarWithMessage;
import com.example.verifit.model.CategoryColours;
import com.example.verifit.model.Exercise;
import com.example.verifit.R;
import com.example.verifit.model.WorkoutSet;
import com.example.verifit.ui.AddExerciseActivity;
import com.example.verifit.ui.LoginActivity;
import com.example.verifit.ui.MainActivity;
import com.example.verifit.verifitrs.WorkoutSetsApi;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import android.widget.ImageView;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


// Adapter for Exercise Class
public class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.MyViewHolder> implements Filterable {

    Context ct;
    ArrayList<Exercise> Exercises;
    ArrayList<Exercise> Exercises_Full; // for search functionality

    // "Show Exercise Details" (Vague 1 du plan de migration, feature FitNotes) -
    // masque par defaut, bascule via ExercisesActivity (menu "Show Details"). Voir
    // setShowDetails()/onBindViewHolder().
    private boolean showDetails = false;

    // Comparateur "Favorite Exercises" (Vague 1) : favoris en premier, ordre stable
    // sinon (Collections.sort est un tri stable en Java) - voir sortFavoritesFirst().
    private static final Comparator<Exercise> FAVORITES_FIRST_COMPARATOR = new Comparator<Exercise>()
    {
        @Override
        public int compare(Exercise a, Exercise b)
        {
            boolean aFavorite = isFavorite(a);
            boolean bFavorite = isFavorite(b);
            if (aFavorite == bFavorite)
            {
                return 0;
            }
            return aFavorite ? -1 : 1;
        }
    };

    private static boolean isFavorite(Exercise exercise)
    {
        return exercise.getFavorite() != null && exercise.getFavorite();
    }

    // Adapter Constructor 7 minute mark
    public ExerciseAdapter(Context ct, ArrayList<Exercise> Exercises)
    {
        this.ct = ct;
        this.Exercises = new ArrayList<>(Exercises); // If you this is changed to: this.Exercises = Exercises; then on search diary activity will not recognize known exercises
        this.Exercises_Full = new ArrayList<>(Exercises);
        sortFavoritesFirst();
    }

    // "Favorite Exercises" (Vague 1) : remonte les exercices favoris en tete de liste -
    // appele a la construction et a chaque bascule Favorite/Unfavorite (voir
    // onMenuItemClick()) pour un feedback immediat, comme sur FitNotes.
    private void sortFavoritesFirst()
    {
        Collections.sort(Exercises, FAVORITES_FIRST_COMPARATOR);
        Collections.sort(Exercises_Full, FAVORITES_FIRST_COMPARATOR);
    }

    // Appele par ExercisesActivity (menu "Show Details") - voir onBindViewHolder().
    public void setShowDetails(boolean showDetails)
    {
        this.showDetails = showDetails;
        notifyDataSetChanged();
    }

    // "Show Exercise Details" (Vague 1) : "12 séances - il y a 3 jours" / "Jamais
    // utilisé" si l'exercice n'a encore aucune séance loggée.
    private String formatExerciseDetails(Exercise exercise)
    {
        int workoutCount = MainActivity.dataStorage.getExerciseWorkoutCount(exercise.getName());
        String lastUsedDate = MainActivity.dataStorage.getExerciseLastUsedDate(exercise.getName());

        String plural = workoutCount > 1 ? "séances" : "séance";

        if (lastUsedDate == null)
        {
            return workoutCount + " " + plural;
        }

        return workoutCount + " " + plural + " - " + formatElapsedSince(lastUsedDate);
    }

    // Convertit une date yyyy-MM-dd (WorkoutDay.getDate()) en temps ecoule relatif
    // ("Aujourd'hui"/"Hier"/"X jours") - meme convention de format que
    // DataStorage.sortWorkoutDaysDate().
    private String formatElapsedSince(String isoDate)
    {
        try
        {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(isoDate);
            long diffDays = (new Date().getTime() - date.getTime()) / (24L * 60 * 60 * 1000);

            if (diffDays <= 0)
            {
                return "il y a aujourd'hui";
            }
            if (diffDays == 1)
            {
                return "il y a 1 jour";
            }
            return "il y a " + diffDays + " jours";
        }
        catch (Exception e)
        {
            return isoDate;
        }
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(this.ct);
        View view = inflater.inflate(R.layout.exercise_row,parent,false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position)
    {
        Exercise exercise = Exercises.get(position);

        // Change TextView text
        holder.tv_exercise_name.setText(exercise.getName());
        holder.tv_exercise_bodypart.setText(exercise.getBodyPart());

        // "Category Colours" (Vague 1) : voir com.example.verifit.model.CategoryColours.
        holder.iv_category_colour.setColorFilter(CategoryColours.getColour(exercise.getBodyPart()));

        // "Favorite Exercises" (Vague 1) : etoile visible seulement si favori.
        holder.iv_favorite.setVisibility(isFavorite(exercise) ? View.VISIBLE : View.GONE);

        // "Show Exercise Details" (Vague 1) : masque par defaut, voir setShowDetails().
        if (showDetails)
        {
            holder.tv_exercise_details.setVisibility(View.VISIBLE);
            holder.tv_exercise_details.setText(formatExerciseDetails(exercise));
        }
        else
        {
            holder.tv_exercise_details.setVisibility(View.GONE);
        }

        // Goto Add Exercise Activity
        holder.cardview_exercise_1.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                Intent in = new Intent(ct, AddExerciseActivity.class);
                in.putExtra("exercise",holder.tv_exercise_name.getText().toString());
                ct.startActivity(in);
            }
        });
    }


    public void deleteExercise(String exercise_name)
    {
        for (Iterator<Exercise> exerciseIterator = this.Exercises.iterator(); exerciseIterator.hasNext(); )
        {
            Exercise current_exercise = exerciseIterator.next();

            if(current_exercise.getName().equals(exercise_name))
            {
                exerciseIterator.remove();
                notifyDataSetChanged();
            }
        }
    }


    @Override
    public int getItemCount()
    {
        return this.Exercises.size();
    }


    // Implement Filterable Methods
    @Override
    public Filter getFilter() {
        return exampleFilter;
    }

    private Filter exampleFilter = new Filter() {

        // Return our filtered results
        @Override
        protected FilterResults performFiltering(CharSequence charSequence)
        {
            ArrayList<Exercise> Exercises_Filtered = new ArrayList<Exercise>();

            // Don't filter anything
            if(charSequence == null || charSequence.length() == 0)
            {
                Exercises_Filtered.addAll(Exercises_Full);
            }
            // Something was typed so filter results
            else
            {
                String filterPattern = charSequence.toString().toLowerCase().trim();

                for(int i = 0; i < Exercises.size(); i++)
                {
                    // If search patterns is contained in exercise name then show it
                    if(Exercises.get(i).getName().toLowerCase().contains(filterPattern))
                    {
                        Exercises_Filtered.add(Exercises.get(i));
                    }
                }
            }

            // Return results object to the publishResults method below
            FilterResults results = new FilterResults();
            results.values = Exercises_Filtered;
            return results;
        }

        // Publish Results to the UI thread
        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults)
        {
            Exercises.clear();
            Exercises.addAll((ArrayList)filterResults.values); // Add only the filtered items
            notifyDataSetChanged(); // Update Recycler View
        }
    };

    public class MyViewHolder extends  RecyclerView.ViewHolder implements View.OnLongClickListener, PopupMenu.OnMenuItemClickListener, AdapterView.OnItemSelectedListener {
        TextView tv_exercise_name;
        TextView tv_exercise_bodypart;
        ImageView iv_favorite;
        ImageView iv_category_colour;
        TextView tv_exercise_details;
        CardView cardview_exercise_1;
        String exercise_name;
        String new_exercise_category;
        int current_exercise_category_position;

        public MyViewHolder(@NonNull View itemView)
        {
            super(itemView);
            tv_exercise_name = itemView.findViewById(R.id.tv_date);
            tv_exercise_bodypart = itemView.findViewById(R.id.exercise_bodypart);
            iv_favorite = itemView.findViewById(R.id.iv_favorite);
            iv_category_colour = itemView.findViewById(R.id.iv_category_colour);
            tv_exercise_details = itemView.findViewById(R.id.tv_exercise_details);
            cardview_exercise_1 = itemView.findViewById(R.id.cardview_exercise_1);

            // For PopUp Menu
            cardview_exercise_1.setOnLongClickListener(this);
        }


        // Triggered when someone long presses on cardview_exercise_1
        @Override
        public boolean onLongClick(View view)
        {
            showPopupMenu(view);
            return true;
        }


        private void showPopupMenu(View view)
        {
            PopupMenu popupMenu = new PopupMenu(view.getContext(),view);
            popupMenu.inflate(R.menu.exercises_activity_floating_context_menu);

            // "Favorite Exercises" (Vague 1) : le libelle reflete l'etat actuel de
            // l'exercice sur lequel on vient de faire l'appui long, comme sur FitNotes.
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION)
            {
                MenuItem favoriteItem = popupMenu.getMenu().findItem(R.id.favorite);
                favoriteItem.setTitle(isFavorite(Exercises.get(position)) ? "Unfavorite" : "Favorite");
            }

            popupMenu.setOnMenuItemClickListener(this);
            popupMenu.show();
        }


        @Override
        public boolean onMenuItemClick(MenuItem item)
        {
            // "Favorite Exercises" (Vague 1 du plan de migration, feature FitNotes) :
            // bascule Favorite/Unfavorite, persiste immediatement (meme convention que
            // les autres actions de ce menu) et remonte l'exercice en tete de liste.
            if(item.getItemId() == R.id.favorite)
            {
                int position = getAdapterPosition();
                Exercise exercise = Exercises.get(position);

                MainActivity.dataStorage.setFavoriteExercise(exercise.getName(), !isFavorite(exercise));
                MainActivity.dataStorage.saveKnownExerciseData(ct);

                sortFavoritesFirst();
                notifyDataSetChanged();
                return true;
            }

            // Edit Exercise
            if(item.getItemId() == R.id.edit)
            {


                int position = getAdapterPosition();
                System.out.println("Edit");

                // Prepare to show exercise dialog box
                LayoutInflater inflater = LayoutInflater.from(ct);
                View view = inflater.inflate(R.layout.edit_exercise_dialog,null);
                AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

                // Find views
                Button bt_save = view.findViewById(R.id.bt_login_signup);
                Button bt_cancel = view.findViewById(R.id.bt_cancel);
                EditText et_exercise_name = view.findViewById(R.id.et_exercise_name);
                Spinner spinner = view.findViewById(R.id.spinner);


                // Setup Spinner Stuff
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(ct,R.array.Categories, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                spinner.setOnItemSelectedListener(this);

                // Get Array from xml
                String[] listValue;
                listValue = ct.getResources().getStringArray(R.array.Categories);

                // Find Current Category position
                for(int i = 0; i < listValue.length; i++)
                {
                    if(listValue[i].equals(Exercises.get(position).getBodyPart()))
                    {
                        current_exercise_category_position = i;
                        System.out.println(listValue[i]);
                    }
                }

                // Set edit text and spinner initial values
                exercise_name = Exercises.get(position).getName();
                et_exercise_name.setText(exercise_name);
                spinner.setSelection(current_exercise_category_position);

                // Dismiss Dialog Box
                bt_cancel.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        alertDialog.dismiss();
                    }
                });

                // Replace all instances with new exercise
                bt_save.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        // Get User defined new name
                        String new_exercise_name = et_exercise_name.getText().toString();

                        // Call Edit Exercise if user gave reasonable input
                        if(new_exercise_category != null && !new_exercise_category.isEmpty() && new_exercise_category.length() > 0 && new_exercise_name != null && !new_exercise_name.isEmpty() && new_exercise_name.length() > 0)
                        {
                            SharedPreferences sharedPreferences = new SharedPreferences(ct);
                            if(sharedPreferences.isOfflineMode()) {
                                locallyUpdateExercise(new_exercise_name);
                                alertDialog.dismiss();
                            }
                            else
                            {

                                final LoadingDialog loadingDialog = new LoadingDialog((Activity) ct);
                                loadingDialog.loadingAlertDialog();

                                List<WorkoutSet> to_be_updated_sets = MainActivity.dataStorage.editExerciseGetSets(exercise_name,new_exercise_name,new_exercise_category);
                                WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(ct, ct.getString(R.string.API_ENDPOINT));
                                workoutSetsApi.updateWorkoutSets(to_be_updated_sets, new Callback() {
                                    @Override
                                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                        ((Activity) ct).runOnUiThread(() -> {
                                            alertDialog.dismiss();
                                            loadingDialog.dismissDialog();
                                            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(ct);
                                            snackBarWithMessage.showSnackbar("Can't connect to server");
                                        });
                                    }

                                    @Override
                                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                                        System.out.println(response.toString());

                                        ((Activity) ct).runOnUiThread(() -> {
                                            alertDialog.dismiss();
                                            loadingDialog.dismissDialog();
                                        });

                                        if(200 == response.code())
                                        {
                                            ((Activity) ct).runOnUiThread(() -> {
                                                locallyUpdateExercise(new_exercise_name);
                                                SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(ct);
                                                snackBarWithMessage.showSnackbar("Exercise Updated");
                                            });
                                        }
                                        else
                                        {
                                            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(ct);
                                            snackBarWithMessage.showSnackbar(response.message().toString());
                                        }
                                    }
                                });
                            }
                        }

                        // Tell user to stop fucking around
                        else
                        {
                            Toast.makeText(ct,"Please choose an apropriate name", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

                alertDialog.show();
                return true;
            }

            // Duplicate Exercise: creates a brand new known exercise (same body part,
            // no history) under a new name, leaving the original and all its logged
            // sets untouched. Handy for variants (e.g. "Incline Dumbbell Press" from
            // "Flat Dumbbell Press") without retyping everything or fighting the
            // name-based lookups used everywhere else in the app.
            else if(item.getItemId() == R.id.duplicate)
            {
                int position = getAdapterPosition();
                String source_exercise_name = Exercises.get(position).getName();
                String source_exercise_bodypart = Exercises.get(position).getBodyPart();

                // Reuse the edit dialog layout: same fields (name + category), different title/button and different save behaviour (add instead of rename).
                LayoutInflater inflater = LayoutInflater.from(ct);
                View view = inflater.inflate(R.layout.edit_exercise_dialog,null);
                AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();

                // Find views
                TextView tv_dialog_title = view.findViewById(R.id.tv_date);
                Button bt_save = view.findViewById(R.id.bt_login_signup);
                Button bt_cancel = view.findViewById(R.id.bt_cancel);
                EditText et_exercise_name = view.findViewById(R.id.et_exercise_name);
                Spinner spinner = view.findViewById(R.id.spinner);

                tv_dialog_title.setText("Duplicate Exercise");
                bt_save.setText("Duplicate");

                // Setup Spinner Stuff
                ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(ct,R.array.Categories, android.R.layout.simple_spinner_item);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);
                spinner.setOnItemSelectedListener(this);

                // Get Array from xml
                String[] listValue = ct.getResources().getStringArray(R.array.Categories);

                // Find Current Category position
                for(int i = 0; i < listValue.length; i++)
                {
                    if(listValue[i].equals(source_exercise_bodypart))
                    {
                        current_exercise_category_position = i;
                    }
                }

                // Pre-fill with a "(copy)" suffix so it can't collide with the original,
                // cursor placed at the end so the user can just start typing the real
                // variant name (e.g. "Incline ...").
                String suggested_name = source_exercise_name + " (copy)";
                et_exercise_name.setText(suggested_name);
                et_exercise_name.setSelection(suggested_name.length());
                spinner.setSelection(current_exercise_category_position);
                // Also set directly in case the spinner's selection listener doesn't
                // fire before Duplicate is tapped (mirrors the Edit dialog above).
                new_exercise_category = source_exercise_bodypart;

                bt_cancel.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        alertDialog.dismiss();
                    }
                });

                bt_save.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        String new_exercise_name = et_exercise_name.getText().toString().trim();

                        if(new_exercise_name.isEmpty() || new_exercise_category == null || new_exercise_category.isEmpty())
                        {
                            Toast.makeText(ct,"Please choose an apropriate name", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if(MainActivity.dataStorage.doesExerciseExist(new_exercise_name))
                        {
                            Toast.makeText(ct,"Exercise Already Exists", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Exercise duplicated = MainActivity.dataStorage.duplicateExercise(source_exercise_name, new_exercise_name);

                        if(duplicated != null)
                        {
                            // Honour a category change made in the dialog (duplicateExercise() copies the source's body part by default).
                            if(!new_exercise_category.equals(duplicated.getBodyPart()))
                            {
                                duplicated.setBodyPart(new_exercise_category);
                            }

                            MainActivity.dataStorage.saveKnownExerciseData(ct);

                            Exercises.add(duplicated);
                            Exercises_Full.add(duplicated);
                            notifyDataSetChanged();

                            Toast.makeText(ct,"Exercise Duplicated", Toast.LENGTH_SHORT).show();
                        }

                        alertDialog.dismiss();
                    }
                });

                alertDialog.show();
                return true;
            }

            // Delete Exercise
            else if(item.getItemId() == R.id.delete)
            {
                int position = getAdapterPosition();

                // Prepare to show exercise dialog box
                LayoutInflater inflater = LayoutInflater.from(ct);
                View view = inflater.inflate(R.layout.delete_exercise_dialog,null);
                AlertDialog alertDialog = new AlertDialog.Builder(ct).setView(view).create();


                Button bt_yes = view.findViewById(R.id.bt_yes3);
                Button bt_no = view.findViewById(R.id.bt_no3);


                bt_no.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        alertDialog.dismiss();
                    }
                });

                bt_yes.setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View view)
                    {
                        SharedPreferences sharedPreferences = new SharedPreferences(ct);

                        if(sharedPreferences.isOfflineMode())
                        {
                            locallyDeleteExercise(position, ct);
                        }
                        else
                        {
                            final LoadingDialog loadingDialog = new LoadingDialog((Activity) ct);
                            loadingDialog.loadingAlertDialog();

                            WorkoutSetsApi workoutSetsApi = new WorkoutSetsApi(ct, ct.getString(R.string.API_ENDPOINT));
                            List<WorkoutSet> to_be_deleted_sets = MainActivity.dataStorage.deleteExerciseGetSets(Exercises.get(position).getName());

                            workoutSetsApi.deleteWorkoutSets(to_be_deleted_sets, new Callback() {
                                @Override
                                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                    loadingDialog.dismissDialog();

                                    ((Activity) ct).runOnUiThread(() -> {
                                        SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(ct);
                                        snackBarWithMessage.showSnackbar("Can't connect to server");
                                    });
                                }

                                @Override
                                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {

                                    loadingDialog.dismissDialog();

                                    if(200 == response.code()){

                                        ((Activity) ct).runOnUiThread(() -> {
                                            locallyDeleteExercise(position, ct);
                                            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(ct);
                                            snackBarWithMessage.showSnackbar("Exercise Deleted");
                                        });

                                    }
                                    else
                                    {
                                        ((Activity) ct).runOnUiThread(() -> {
                                            SnackBarWithMessage snackBarWithMessage = new SnackBarWithMessage(ct);
                                            snackBarWithMessage.showSnackbar(response.message().toString());
                                        });
                                    }
                                }
                            });

                        }

                        alertDialog.dismiss();
                    }
                });
                alertDialog.show();
                return true;
            }
            return false;
        }


        public void locallyUpdateExercise(String new_exercise_name)
        {
            MainActivity.dataStorage.editExercise(exercise_name,new_exercise_name,new_exercise_category);
            MainActivity.dataStorage.saveWorkoutData(ct);
            MainActivity.dataStorage.saveKnownExerciseData(ct);
            notifyDataSetChanged();
        }


        public void locallyDeleteExercise(int position, Context ct)
        {
            // Delete Exercise from MainActivity Data Structures
            MainActivity.dataStorage.deleteExercise(Exercises.get(position).getName());

            // Delete Exercise from Adapter's local data structure
            deleteExercise(Exercises.get(position).getName());

            // Save Results
            MainActivity.dataStorage.saveKnownExerciseData(ct);
            MainActivity.dataStorage.saveWorkoutData(ct);
        }

        // Spinner On Item Selected Methods
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
        {
            new_exercise_category = adapterView.getItemAtPosition(i).toString();
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView)
        {
            new_exercise_category = "";
        }
    }
}
